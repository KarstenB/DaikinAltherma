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

/**
 * The {@link DaikinAlthermaConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Karsten Becker - Initial contribution
 */
public class DaikinAlthermaConfiguration {

    /**
     * IP Address for adapter
     */
    public String host="Altherma.home";
    /**
     * Port Address for adapter
     */
    public int port=80;

    /**
     * The polling interval in seconds
     */
    public int interval=60;

}
