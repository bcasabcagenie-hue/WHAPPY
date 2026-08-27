#include <jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

namespace {

struct VulkanReport {
    bool available = false;
    uint32_t apiVersion = VK_API_VERSION_1_0;
    uint32_t vendorId = 0;
    uint32_t deviceId = 0;
    uint32_t driverVersion = 0;
    VkPhysicalDeviceType deviceType = VK_PHYSICAL_DEVICE_TYPE_OTHER;
    std::string deviceName;
    std::string reason;
};

bool hasExtension(VkPhysicalDevice device, const char* expected) {
    uint32_t count = 0;
    if (vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr) != VK_SUCCESS || count == 0) {
        return false;
    }
    std::vector<VkExtensionProperties> extensions(count);
    if (vkEnumerateDeviceExtensionProperties(device, nullptr, &count, extensions.data()) != VK_SUCCESS) {
        return false;
    }
    return std::any_of(extensions.begin(), extensions.end(), [expected](const auto& item) {
        return std::strcmp(item.extensionName, expected) == 0;
    });
}

bool hasGraphicsQueue(VkPhysicalDevice device) {
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
    if (count == 0) return false;
    std::vector<VkQueueFamilyProperties> queues(count);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, queues.data());
    return std::any_of(queues.begin(), queues.end(), [](const auto& queue) {
        return queue.queueCount > 0 && (queue.queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0;
    });
}

int scoreDevice(const VkPhysicalDeviceProperties& properties) {
    int score = 0;
    if (properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU) score += 400;
    if (properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU) score += 300;
    if (properties.deviceType == VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU) score += 180;
    score += static_cast<int>(VK_API_VERSION_MINOR(properties.apiVersion)) * 20;
    score += static_cast<int>(properties.limits.maxImageDimension2D / 1024);
    return score;
}

VulkanReport inspectVulkan() {
    VulkanReport report;
    uint32_t loaderVersion = VK_API_VERSION_1_0;
    auto enumerateVersion = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
        vkGetInstanceProcAddr(nullptr, "vkEnumerateInstanceVersion")
    );
    if (enumerateVersion != nullptr) enumerateVersion(&loaderVersion);

    VkApplicationInfo appInfo{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    appInfo.pApplicationName = "WAPI Play";
    appInfo.applicationVersion = VK_MAKE_VERSION(2, 2, 0);
    appInfo.pEngineName = "WAPI Vulkan Engine";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = std::min(loaderVersion, VK_API_VERSION_1_1);

    VkInstanceCreateInfo instanceInfo{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    instanceInfo.pApplicationInfo = &appInfo;
    VkInstance instance = VK_NULL_HANDLE;
    const VkResult instanceResult = vkCreateInstance(&instanceInfo, nullptr, &instance);
    if (instanceResult != VK_SUCCESS) {
        report.reason = "vkCreateInstance=" + std::to_string(instanceResult);
        return report;
    }

    uint32_t deviceCount = 0;
    VkResult enumerateResult = vkEnumeratePhysicalDevices(instance, &deviceCount, nullptr);
    if (enumerateResult != VK_SUCCESS || deviceCount == 0) {
        report.reason = "aucun GPU Vulkan";
        vkDestroyInstance(instance, nullptr);
        return report;
    }
    std::vector<VkPhysicalDevice> devices(deviceCount);
    vkEnumeratePhysicalDevices(instance, &deviceCount, devices.data());

    VkPhysicalDevice selected = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties selectedProperties{};
    int selectedScore = -1;
    for (const auto device : devices) {
        VkPhysicalDeviceProperties properties{};
        vkGetPhysicalDeviceProperties(device, &properties);
        if (!hasGraphicsQueue(device)) continue;
        if (!hasExtension(device, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) continue;
        const int score = scoreDevice(properties);
        if (score > selectedScore) {
            selected = device;
            selectedProperties = properties;
            selectedScore = score;
        }
    }

    if (selected == VK_NULL_HANDLE) {
        report.reason = "GPU sans file graphique ou swapchain";
        vkDestroyInstance(instance, nullptr);
        return report;
    }

    report.available = true;
    report.apiVersion = selectedProperties.apiVersion;
    report.vendorId = selectedProperties.vendorID;
    report.deviceId = selectedProperties.deviceID;
    report.driverVersion = selectedProperties.driverVersion;
    report.deviceType = selectedProperties.deviceType;
    report.deviceName = selectedProperties.deviceName;
    report.reason = "prêt";
    vkDestroyInstance(instance, nullptr);
    return report;
}

std::string typeName(VkPhysicalDeviceType type) {
    switch (type) {
        case VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU: return "discrete";
        case VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU: return "integrated";
        case VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU: return "virtual";
        case VK_PHYSICAL_DEVICE_TYPE_CPU: return "cpu";
        default: return "other";
    }
}

std::string toJson(const VulkanReport& report) {
    const uint32_t major = VK_API_VERSION_MAJOR(report.apiVersion);
    const uint32_t minor = VK_API_VERSION_MINOR(report.apiVersion);
    const uint32_t patch = VK_API_VERSION_PATCH(report.apiVersion);
    return "{\"available\":" + std::string(report.available ? "true" : "false") +
        ",\"apiVersion\":\"" + std::to_string(major) + "." + std::to_string(minor) + "." + std::to_string(patch) + "\"" +
        ",\"deviceName\":\"" + report.deviceName + "\"" +
        ",\"deviceType\":\"" + typeName(report.deviceType) + "\"" +
        ",\"vendorId\":" + std::to_string(report.vendorId) +
        ",\"deviceId\":" + std::to_string(report.deviceId) +
        ",\"driverVersion\":" + std::to_string(report.driverVersion) +
        ",\"reason\":\"" + report.reason + "\"}";
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_whappy_chat_WapiVulkanCapabilities_nativeInspect(
    JNIEnv* env,
    jclass
) {
    const std::string report = toJson(inspectVulkan());
    return env->NewStringUTF(report.c_str());
}

