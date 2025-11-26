MY_LOCAL_PATH := $(call my-dir)

# 先加载子目录的makefiles（包括toupcam库）
include $(call all-subdir-makefiles)

# 然后构建jnicam
LOCAL_PATH := $(MY_LOCAL_PATH)
include $(CLEAR_VARS)
LOCAL_MODULE := jnicam
LOCAL_CFLAGS    := -Wno-narrowing -Wno-deprecated-declarations -Werror -O2 -fPIC -fvisibility=hidden
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libusbcam
LOCAL_SRC_FILES := jnicam.cpp
LOCAL_LDLIBS    := -llog
LOCAL_SHARED_LIBRARIES := toupcam
include $(BUILD_SHARED_LIBRARY)