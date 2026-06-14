# Running Minecraft on Linux ppc64le (POWER) with LWJGL

This is a runbook for getting modern Minecraft (LWJGL 3.4.1, e.g. MC 26.1.x) running
on **Linux ppc64le** through **PrismLauncher**. It documents a real bug in the
official LWJGL ppc64le natives and two ways to fix it.

Verified on: Fedora 44 (POWER9), AMD Radeon RX 6600 XT (RADV/radeonsi), OpenJDK 25 ppc64le,
PrismLauncher 11.0.2, Minecraft 26.1.2 / LWJGL 3.4.1.

---

## 1. Background: LWJGL natives on ppc64le exist

Minecraft's native dependencies are all `org.lwjgl:*`. LWJGL publishes official
ppc64le builds for the 11 native modules Minecraft uses, at
`https://build.lwjgl.org/release/<version>/bin/`:

```
core (lwjgl), freetype, glfw, jemalloc, openal,
opengl, shaderc, spvc, stb, tinyfd, vma
```

`lwjgl-vulkan` has **no** Linux native (it loads the system Vulkan loader),
so it does not need a ppc64le build.

In a launcher, these are referenced with the extra-arch classifier convention used
for arm64/arm32/riscv64, e.g.:

```
org.lwjgl:lwjgl-glfw-natives-linux-ppc64le:3.4.1-lwjgl.1
  rules: [{ "action": "allow", "os": { "name": "linux-ppc64le" } }]
  url:   https://build.lwjgl.org/release/3.4.1/bin/lwjgl-glfw/lwjgl-glfw-natives-linux-ppc64le.jar
```

PrismLauncher maps the running `os.arch` (ppc64le) to the `linux-ppc64le` library
entries, so once those 11 entries are present in the instance's lwjgl3 component it
downloads and uses them automatically.

---

## 2. The bug: `Failed to prepare libffi CIF: 2`

With the natives in place, the game crashes immediately at window/GLFW init:

```
java.lang.ExceptionInInitializerError
    at com.mojang.blaze3d.platform.GLX._initGlfw(GLX.java:46)
Caused by: java.lang.IllegalStateException: Failed to prepare libffi CIF: 2
    at org.lwjgl.system.APIUtil.apiCreateCIF(APIUtil.java:633)
    at org.lwjgl.glfw.GLFWErrorCallbackI.<clinit>(GLFWErrorCallbackI.java:23)
```

`2` is libffi's `FFI_BAD_ABI`. LWJGL builds a libffi *call interface* for every
native callback (the GLFW error callback is the first one), and libffi rejects the
ABI value LWJGL hands it.

### Root cause

The official `release/3.4.1` ppc64le `liblwjgl.so` is **internally inconsistent**:

| Component inside `liblwjgl.so` | ABI enum it was built with |
|---|---|
| the libffi JNI wrapper (`LibFFI.c`) | 32-bit **SYSV** branch → `FFI_DEFAULT_ABI = 28`, `FFI_LAST_ABI = 32` |
| the bundled `libffi.a` | 64-bit **POWERPC64/LINUX** branch → valid ABIs `8–15`, `FFI_LAST_ABI = 16` |

So the wrapper reports `FFI_DEFAULT_ABI = 28`, but the linked libffi only accepts
`{8,9,10,11,14,15}`. `28 >= 16`, so **every** CIF/callback creation fails.

Why: libffi's `ffitarget.h` only selects the 64-bit branch when the macro `POWERPC64`
is defined, which in turn requires **both** `__powerpc64__` (a compiler builtin) **and**
`POWERPC` (normally defined by libffi's autotools build, `-DPOWERPC`). LWJGL's build
compiled the wrapper **without `-DPOWERPC`**, so it fell into the 32-bit SYSV branch.
The `libffi.a` itself was built by autotools (with `POWERPC`), so it used the correct
64-bit branch. The two halves disagree.

The correct `FFI_DEFAULT_ABI` for a 64-bit ppc64le Linux build is:

```
FFI_LINUX(8) | FFI_LINUX_STRUCT_ALIGN(1) | FFI_LINUX_LONG_DOUBLE_128(2) | FFI_LINUX_LONG_DOUBLE_IEEE128(4) = 15
```

on a toolchain with IEEE-128 `long double` (Fedora ppc64le), or `11` on a
toolchain with the older IBM double-double `long double`. Both are accepted by the
bundled `libffi.a`.

> This is an upstream LWJGL packaging bug; it affects **every** ppc64le user, not just
> Fedora.

---

## 3. Fix A — patch the shipped native (no rebuild)

`FFI_DEFAULT_ABI()` in the core `liblwjgl.so` is literally a one-instruction function:

```asm
li   r3, 28      # bytes (ppc64le, little-endian): 1c 00 60 38
blr              # bytes:                          20 00 80 4e
```

We change the immediate `28` → `15`. That single byte (`1c` → `0f`) makes the wrapper
report the ABI the bundled libffi actually accepts. **Only the `core` (`lwjgl`) jar
needs this** — it's the only module that bundles libffi.

### 3.1 Build the patched jar

Adjust `USER` / instance id / version to your setup. Run on the ppc64le box:

```bash
USER=$(whoami)
PRISM="$HOME/.local/share/PrismLauncher"
COREDIR="$PRISM/libraries/org/lwjgl/lwjgl-natives-linux-ppc64le/3.4.1-lwjgl.1"
JAR="$COREDIR/lwjgl-natives-linux-ppc64le-3.4.1-lwjgl.1.jar"

# stable home for the patched jar (survives Prism re-validation, see Fix A.2)
FIXDIR="$HOME/.local/share/lwjgl-ppc64le-fix"
mkdir -p "$FIXDIR"
PJ="$FIXDIR/lwjgl-natives-linux-ppc64le-3.4.1-lwjgl.1.jar"

# keep a pristine backup of the upstream jar
[ -f "$JAR.orig" ] || cp "$JAR" "$JAR.orig"

# extract, patch the one byte, repack
W=$(mktemp -d); cd "$W"
cp "$JAR.orig" base.jar
unzip -o -q base.jar -d ext
python3 - ext/linux/ppc64le/org/lwjgl/liblwjgl.so <<'PY'
import sys, re
p = sys.argv[1]
b = bytearray(open(p, "rb").read())
pat = bytes.fromhex("1c006038" + "2000804e")   # li r3,28 ; blr
idx = [m.start() for m in re.finditer(re.escape(pat), bytes(b))]
assert len(idx) == 1 and b[idx[0]] == 0x1c, idx
b[idx[0]] = 0x0f                                 # li r3,15
open(p, "wb").write(b)
print("patched FFI_DEFAULT_ABI 28 -> 15 at", hex(idx[0]))
PY

cp base.jar "$PJ"
( cd ext && zip -q "$PJ" linux/ppc64le/org/lwjgl/liblwjgl.so )
cp "$PJ" "$JAR"     # install as the library too

SHA=$(sha1sum "$PJ" | cut -d' ' -f1)
SIZE=$(stat -c %s "$PJ")
echo "patched jar: sha1=$SHA size=$SIZE  url=file://$PJ"
```

### 3.2 Stop PrismLauncher from reverting it

PrismLauncher re-downloads every library from its metadata `url` and validates the
result against the metadata `sha1` in:

```
$PRISM/instances/<INSTANCE>/patches/org.lwjgl3.json
```

If you only patch the jar, Prism re-downloads the upstream (broken) jar and overwrites
your patch. If you only bump the `sha1`, the upstream download fails validation
("Failed to finalize validators"). The fix is to point the entry at your **local
patched jar via a `file://` URL** with the matching `sha1`/`size`, so download and
validation are self-consistent:

```bash
PATCH="$PRISM/instances/<INSTANCE>/patches/org.lwjgl3.json"
cp "$PATCH" "$PATCH.bak"

jq --arg sha "$SHA" --argjson size "$SIZE" --arg url "file://$PJ" '
  (.libraries[]
   | select(.name=="org.lwjgl:lwjgl-natives-linux-ppc64le:3.4.1-lwjgl.1").downloads.artifact)
  |= (.sha1=$sha | .size=$size | .url=$url)
' "$PATCH.bak" > "$PATCH"

# force LWJGL to re-extract the patched .so on next launch
rm -rf /tmp/lwjgl_$USER
```

Relaunch. Done.

> Caveat: if PrismLauncher re-syncs the lwjgl3 component from its meta server, it can
> overwrite `org.lwjgl3.json` and re-introduce the upstream jar. If the crash returns
> after a Prism update, re-apply step 3.2.

---

## 4. Fix B — rebuild correct natives (permanent, upstream)

The real fix is a one-line change in the LWJGL build so the wrapper is compiled with
`-DPOWERPC` (mirrors the existing `-DX86_64` for the x86 libffi header).

In `config/linux/build.xml`, in the `<compiler>` `<source>` args for the core module:

```xml
<arg value="-DX86_64"  if:set="build.arch.x64"/>      <!-- for libffi/x86/ffitarget.h -->
<arg value="-DPOWERPC" if:set="build.arch.ppc64le"/>  <!-- for libffi/ppc64le/ffitarget.h: selects the 64-bit ABI branch (POWERPC64) -->
```

Rebuilding the core native for ppc64le then yields `FFI_DEFAULT_ABI = 15` (or `11`),
consistent with the bundled `libffi.a`, and no byte-patch is needed. Repackage the
core `natives-linux-ppc64le` jar and host/point your launcher at it.

This should be reported to / merged into upstream LWJGL so the official
`build.lwjgl.org` ppc64le natives are fixed for everyone.

---

## 5. Verifying the fix

A quick standalone check (no launcher) — should print `FFI_DEFAULT_ABI = 15` and
`glfwInit()=true`:

```java
// GlfwCheck.java
import org.lwjgl.glfw.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.libffi.LibFFI.*;
public class GlfwCheck {
  public static void main(String[] a) {
    System.out.println("FFI_DEFAULT_ABI = " + FFI_DEFAULT_ABI);   // expect 15 (was 28)
    GLFWErrorCallback.createPrint(System.err).set();              // the call that used to crash
    System.out.println("glfwInit()=" + glfwInit());
  }
}
```

```bash
LIB="$PRISM/libraries"
CP="$LIB/org/lwjgl/lwjgl/3.4.1/lwjgl-3.4.1-unsafe.jar"
CP="$CP:$LIB/org/lwjgl/lwjgl-glfw/3.4.1/lwjgl-glfw-3.4.1.jar"
CP="$CP:$LIB/org/lwjgl/lwjgl-natives-linux-ppc64le/3.4.1-lwjgl.1/lwjgl-natives-linux-ppc64le-3.4.1-lwjgl.1.jar"
CP="$CP:$LIB/org/lwjgl/lwjgl-glfw-natives-linux-ppc64le/3.4.1-lwjgl.1/lwjgl-glfw-natives-linux-ppc64le-3.4.1-lwjgl.1.jar"
javac -cp "$CP" GlfwCheck.java -d .
rm -rf /tmp/lwjgl_$USER
java -cp ".:$CP" GlfwCheck
```

---

## 6. Notes / unrelated warnings (harmless)

- `No versions of Java were found for ... linux-power64le ... Using the default one` —
  Prism can't auto-match a Java component for ppc64le but falls back to system `java`
  (OpenJDK ppc64le). Fine.
- `[LWJGL] Incompatible Java and native library versions detected` — soft warning from
  mixing the `3.4.1` core classes with the `3.4.1-lwjgl.1` native build string.
  Non-fatal.
- `Failed to load plugin 'libdecor-gtk.so'` — only appears with no desktop/display
  (e.g. over SSH). Irrelevant under a real session.
- Minecraft renders with OpenGL (not Vulkan); with mesa/radeonsi on a supported GPU you
  get hardware GL, otherwise llvmpipe (software) is the fallback.
