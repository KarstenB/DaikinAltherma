/**
 * Copyright (c) 2010-2019 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.daikinaltherma.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.type.ChannelGroupTypeUID;

/**
 * The {@link DaikinAlthermaBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Karsten Becker - Initial contribution
 */
@NonNullByDefault
public class DaikinAlthermaBindingConstants {

    static final String BINDING_ID = "daikinaltherma";

    public static final String PARAM_HOST="host";
    public static final String PARAM_PORT="port";
    public static final String PARAM_TYPE="type";
    public static final String PARAM_SW_VERSION="softwareVersion";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_ADAPTER = new ThingTypeUID(BINDING_ID, "adapter");

    // List of all Channel ids
    public static final String CHANNEL_1 = "channel1";

    public static final ChannelGroupTypeUID CHANNEL_GROUP = new ChannelGroupTypeUID(BINDING_ID, "top-level-group");
}
