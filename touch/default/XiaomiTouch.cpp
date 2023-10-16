//
// Copyright (C) 2023 Paranoid Android
//
// SPDX-License-Identifier: Apache-2.0
//

#define TAG "XiaomiTouchHAL"

#include "XiaomiTouch.h"

#include <android/binder_manager.h>
#include <android-base/logging.h>
#include <android-base/unique_fd.h>

#include <algorithm>
#include <cassert>
#include <cerrno>
#include <chrono>

#include <fcntl.h>
#include <signal.h>
#include <sys/ioctl.h>

// XiaomiTouch device declarations
#define SET_CUR_VALUE 0
#define TOUCH_DEV_PATH "/dev/xiaomi-touch"
#define TOUCH_ID 0
#define TOUCH_MAGIC 'T'
#define TOUCH_IOC_SET_CUR_VALUE _IO(TOUCH_MAGIC, SET_CUR_VALUE)

using file_fd = android::base::unique_fd;

namespace aidl::vendor::aospa::xiaomitouch {

::ndk::ScopedAStatus XiaomiTouch::setModeValue(int32_t mode, int32_t value) {
    if (!DISABLE_DEBUG) LOG(INFO) << TAG << ": " << "setModeValue called with value = " << value;

    file_fd fd(open(TOUCH_DEV_PATH, O_RDWR));

    int buf[3] = {TOUCH_ID, mode, value};

    if (fd.get() == -1) {
        if (!DISABLE_DEBUG) LOG(ERROR) << TAG << ": " << "Failed to open: " << TOUCH_DEV_PATH;
    } else {
        if (ioctl(fd.get(), TOUCH_IOC_SET_CUR_VALUE, &buf) == -1) {
            if (!DISABLE_DEBUG) LOG(ERROR) << TAG << ": " << "Failed to write to: " << TOUCH_DEV_PATH;
        } else {
            if (!DISABLE_DEBUG) LOG(INFO) << TAG << ": " << "Wrote touch mode value as: " << value
                << " for mode: " << mode;
        }
    }

    return ::ndk::ScopedAStatus::ok();
}

XiaomiTouch::XiaomiTouch(void) {
    if (!DISABLE_DEBUG) LOG(INFO) << TAG << ": " << "XiaomiTouch: Constructor";
}

}