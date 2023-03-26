/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 * MACHINE GENERATED FILE, DO NOT EDIT
 */
package vulkan.templates

import org.lwjgl.generator.*
import vulkan.*

val KHR_external_semaphore = "KHRExternalSemaphore".nativeClassVK("KHR_external_semaphore", type = "device", postfix = "KHR") {
    documentation =
        """
        An application using external memory may wish to synchronize access to that memory using semaphores. This extension enables an application to create semaphores from which non-Vulkan handles that reference the underlying synchronization primitive can be exported.

        <h5>Promotion to Vulkan 1.1</h5>
        All functionality in this extension is included in core Vulkan 1.1, with the KHR suffix omitted. The original type, enum and command names are still available as aliases of the core functionality.

        <h5>VK_KHR_external_semaphore</h5>
        <dl>
            <dt><b>Name String</b></dt>
            <dd>{@code VK_KHR_external_semaphore}</dd>

            <dt><b>Extension Type</b></dt>
            <dd>Device extension</dd>

            <dt><b>Registered Extension Number</b></dt>
            <dd>78</dd>

            <dt><b>Revision</b></dt>
            <dd>1</dd>

            <dt><b>Extension and Version Dependencies</b></dt>
            <dd>{@link KHRExternalSemaphoreCapabilities VK_KHR_external_semaphore_capabilities}</dd>

            <dt><b>Deprecation state</b></dt>
            <dd><ul>
                <li><em>Promoted</em> to <a target="_blank" href="https://registry.khronos.org/vulkan/specs/1.3-extensions/html/vkspec.html\#versions-1.1-promotions">Vulkan 1.1</a></li>
            </ul></dd>

            <dt><b>Contact</b></dt>
            <dd><ul>
                <li>James Jones <a target="_blank" href="https://github.com/KhronosGroup/Vulkan-Docs/issues/new?body=[VK_KHR_external_semaphore]%20@cubanismo%250A*Here%20describe%20the%20issue%20or%20question%20you%20have%20about%20the%20VK_KHR_external_semaphore%20extension*">cubanismo</a></li>
            </ul></dd>
        </dl>

        <h5>Other Extension Metadata</h5>
        <dl>
            <dt><b>Last Modified Date</b></dt>
            <dd>2016-10-21</dd>

            <dt><b>IP Status</b></dt>
            <dd>No known IP claims.</dd>

            <dt><b>Interactions and External Dependencies</b></dt>
            <dd><ul>
                <li>Promoted to Vulkan 1.1 Core</li>
            </ul></dd>

            <dt><b>Contributors</b></dt>
            <dd><ul>
                <li>Faith Ekstrand, Intel</li>
                <li>Jesse Hall, Google</li>
                <li>Tobias Hector, Imagination Technologies</li>
                <li>James Jones, NVIDIA</li>
                <li>Jeff Juliano, NVIDIA</li>
                <li>Matthew Netsch, Qualcomm Technologies, Inc.</li>
                <li>Ray Smith, ARM</li>
                <li>Lina Versace, Google</li>
            </ul></dd>
        </dl>
        """

    IntConstant(
        "The extension specification version.",

        "KHR_EXTERNAL_SEMAPHORE_SPEC_VERSION".."1"
    )

    StringConstant(
        "The extension name.",

        "KHR_EXTERNAL_SEMAPHORE_EXTENSION_NAME".."VK_KHR_external_semaphore"
    )

    EnumConstant(
        "Extends {@code VkStructureType}.",

        "STRUCTURE_TYPE_EXPORT_SEMAPHORE_CREATE_INFO_KHR".."1000077000"
    )

    EnumConstant(
        "Extends {@code VkSemaphoreImportFlagBits}.",

        "SEMAPHORE_IMPORT_TEMPORARY_BIT_KHR".enum(0x00000001)
    )
}