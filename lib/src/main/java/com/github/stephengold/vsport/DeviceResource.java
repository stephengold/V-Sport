/*
 Copyright (c) 2023, Stephen Gold
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 1. Redistributions of source code must retain the above copyright notice, this
    list of conditions and the following disclaimer.

 2. Redistributions in binary form must reproduce the above copyright notice,
    this list of conditions and the following disclaimer in the documentation
    and/or other materials provided with the distribution.

 3. Neither the name of the copyright holder nor the names of its
    contributors may be used to endorse or promote products derived from
    this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.stephengold.vsport;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A resource that persists across LogicalDevice changes but needs updating each
 * time.
 *
 * @author Stephen Gold sgold@sonic.net
 */
abstract class DeviceResource implements Comparable<DeviceResource> {
    // *************************************************************************
    // fields

    /**
     * assign IDs sequentially
     */
    final private static AtomicInteger nextId = new AtomicInteger();
    /**
     * uniquely identify this instance for the purpose of searching
     */
    final private int lookupId;
    // *************************************************************************
    // constructors

    /**
     * Instantiate a new resource.
     */
    DeviceResource() {
        this.lookupId = nextId.getAndIncrement();
        LogicalDevice.trackResource(this);
    }
    // *************************************************************************
    // new methods exposed

    /**
     * Update this object during a device change.
     *
     * @param nextDevice the current device if it's just been created, or null
     * if the current device is about to be destroyed
     */
    abstract void updateLogicalDevice(LogicalDevice nextDevice);
    // *************************************************************************
    // new protected methods

    /**
     * Callback when the subclass is being destroyed.
     */
    void destroy() {
        LogicalDevice.stopTrackingResource(this);
    }
    // *************************************************************************
    // Comparable methods

    /**
     * Compare with another resource to establish search priority.
     *
     * @param otherResource (not null, unaffected)
     * @return 0 if this equals {@code otherResource}; negative if this comes
     * before {@code otherResource}; positive if this comes after
     * {@code otherResource}
     */
    @Override
    public int compareTo(DeviceResource otherResource) {
        int result = Integer.compare(lookupId, otherResource.lookupId());
        return result;
    }
    // *************************************************************************
    // private methods

    /**
     * Return the lookup ID of this resource.
     *
     * @return the
     */
    private int lookupId() {
        return lookupId;
    }
}
