#
# Copyright (C) 2020-2022 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := $(call my-dir)

ifneq ($(filter haydn lisa mars odin redwood renoir star venus,$(TARGET_DEVICE)),)
include $(call all-makefiles-under,$(LOCAL_PATH))

include $(CLEAR_VARS)

WIFI_FIRMWARE_SYMLINKS := $(TARGET_OUT_VENDOR)/firmware/wlan/qca_cld/
$(WIFI_FIRMWARE_SYMLINKS): $(LOCAL_INSTALLED_MODULE)
	@echo "Creating wifi firmware symlinks: $@"
	mkdir -p $@
	$(hide) ln -sf /vendor/etc/wifi/WCNSS_qcom_cfg.ini $@/WCNSS_qcom_cfg.ini

ALL_DEFAULT_INSTALLED_MODULES += $(WIFI_FIRMWARE_SYMLINKS)

endif
