LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	GravitySensor.cpp \
	LinearAccelerationSensor.cpp \
	RotationVectorSensor.cpp \
    SensorService.cpp \
    SensorInterface.cpp \
    SensorDevice.cpp \
    SecondOrderLowPassFilter.cpp

LOCAL_CFLAGS:= -DLOG_TAG=\"SensorService\"

# need "-lrt" on Linux simulator to pick up clock_gettime
ifeq ($(TARGET_SIMULATOR),true)
	ifeq ($(HOST_OS),linux)
		LOCAL_LDLIBS += -lrt -lpthread
	endif
endif

LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libhardware \
	libutils \
	libbinder \
	libui \
	libgui



LOCAL_MODULE:= libsensorservice

include $(BUILD_SHARED_LIBRARY)
