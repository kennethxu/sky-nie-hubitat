/**
 *      Mini Smart Plug v2.1.1(HUBITAT)
 *
 *  	Models: MINOSTON (MP21Z) And Eva Logik (ZW30) / MINOSTON (MS10Z)
 *
 *  Author:
 *   winnie (sky-nie)
 *
 *	Documentation:
 *
 *  Changelog:
 *
 *    2.1.1 (09/07/2021)
 *      - Syntax format compliance adjustment
 *      - delete dummy code
 *
 *    2.1.0 (09/04/2021)
 *      - remove the preferences item "createButton", Fixedly create a child button
 *        Restrict its use based on fingerprints--because the child buttons is not visible to the user .
 *      - fix a bug: when isButtonAvailable() return false,getLedModeParam is conflict with getPaddleControlParam
 *      - Simplify the code, Syntax format compliance adjustment
 *
 *    2.0.2 (09/02/2021)
 *    2.0.1 (08/27/2021)
 *      - Syntax format compliance adjustment
 *      - fix some bugs
 *
 *    2.0.0 (08/26/2021)
 *      - add new products supported
 *
 *    1.0.4 (07/13/2021)
 *      - Syntax format compliance adjustment
 *      - delete dummy code
 *
 *    1.0.3 (07/12/2021)
 *    1.0.2 (07/07/2021)
 *      - delete dummy code
 *
 *    1.0.1 (03/17/2021)
 *      - Simplify the code, delete dummy code
 *
 *    1.0.0 (03/11/2021)
 *      - Initial Release
 *
 * Reference：
 *  https://github.com/krlaframboise/SmartThings/blob/master/devicetypes/krlaframboise/eva-logik-in-wall-smart-switch.src/eva-logik-in-wall-smart-switch.groovy
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
import groovy.json.JsonOutput

metadata {
    definition (
            name: "Mini Smart Plug",
            namespace: "sky-nie",
            author: "winnie",
            mnmn: "SmartThings",
            vid:"generic-switch",
            importUrl: "https://raw.githubusercontent.com/sky-nie/hubitat/main/mini-smart-plug.src/mini-smart-plug.groovy"
    ) {
        capability "Actuator"
        capability "Sensor"
        capability "Switch"
        capability "Configuration"
        capability "Refresh"
        capability "Health Check"

        attribute "firmwareVersion", "string"
        attribute "syncStatus", "string"

//        fingerprint mfr: "0312", prod: "C000", model: "C009", deviceJoinName: "Minoston Outlet", ocfDeviceType: "oic.d.smartplug" // old MP21Z
//        fingerprint mfr: "0312", prod: "FF00", model: "FF0C", deviceJoinName: "Minoston Outlet", ocfDeviceType: "oic.d.smartplug" //MP21Z Minoston Mini Smart Plug
        fingerprint mfr: "0312", prod: "AC01", model: "4001", deviceJoinName: "New One Outlet",  ocfDeviceType: "oic.d.smartplug" // N4001 New One  Mini Smart Plug
//        fingerprint mfr: "0312", prod: "EE00", model: "EE01", deviceJoinName: "Minoston Switch", ocfDeviceType: "oic.d.switch"  //MS10ZS Minoston Smart Switch
        fingerprint mfr: "0312", prod: "EE00", model: "EE03", deviceJoinName: "Minoston Switch", ocfDeviceType: "oic.d.switch"    //MS12ZS Minoston Smart on/off Toggle Switch
//        fingerprint mfr: "0312", prod: "A000", model: "A005", deviceJoinName: "Evalogik Switch", ocfDeviceType: "oic.d.switch"  //ZW30
        fingerprint mfr: "0312", prod: "BB00", model: "BB01", deviceJoinName: "Evalogik Switch", ocfDeviceType: "oic.d.switch"    //ZW30S Evalogik Smart on/off Switch
        fingerprint mfr: "0312", prod: "BB00", model: "BB03", deviceJoinName: "Evalogik Switch", ocfDeviceType: "oic.d.switch"    //ZW30TS Evalogik Smart on/off Toggle Switch
    }

    preferences {
        getConfigParamInput(ledModeParam)
        getConfigParamInput(autoOffIntervalParam)
        getConfigParamInput(autoOnIntervalParam)
        getConfigParamInput(powerFailureRecoveryParam)
        input "disclaimer", "paragraph",
                title: "WARNING",
                description: "Configuring for 'Paddle Control'is only valid for the devices with product number of MS10ZS, MS12ZS, ZW30, ZW30S, ZW30TS(one of them)",
                required: false
        getConfigParamInput(paddleControlParam)
    }
}

private getConfigParamInput(param) {
    if (param.range) {
        input "configParam${param.num}", "number", title: "${param.name}:", required: false, defaultValue: "${param.value}", range: param.range
    } else {
        input "configParam${param.num}", "enum", title: "${param.name}:", required: false, defaultValue: "${param.value}", options: param.options
    }
}

private addChildButton() {
    log.warn "Creating Button Device"
    def child = addChildDevice(
            "hubitat",
            "Virtual Button",
            "${device.deviceNetworkId}-2",
            [
                completedSet: true,
                isComponent: false,
                label: "plugButton",
                componentLabel: "${device.displayName[0..-8]} Button"
            ]
    )
    child?.sendEvent(name: "pushed", value: 1)
    child?.sendEvent(name: "down", value: 1)
    child?.sendEvent(name: "down_2x", value: 1)
    child?.sendEvent(name: "up", value: 1)
    child?.sendEvent(name: "up_2x", value: 1)
    child?.sendEvent(name:"numberOfButtons", value:2, displayed:false)
    sendButtonEvent("pushed")
    return child
}

def installed() {
    logDebug "installed()..."
    sendEvent(name: "checkInterval", value: checkInterval, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

private static def getCheckInterval() {
    // These are battery-powered devices, and it's not very critical
    // to know whether they're online or not – 12 hrs
    return (60 * 60 * 3) + (5 * 60)
}

def updated() {
    if (!isDuplicateCommand(state.lastUpdated, 5000)) {
        state.lastUpdated = new Date().time
        logDebug "updated()..."
        if (device.latestValue("checkInterval") != checkInterval) {
            sendEvent(name: "checkInterval", value: checkInterval, displayed: false)
        }
        if (isButtonAvailable() && !childDevices) {
            try {
                addChildButton()
            } catch (ex) {
                log.error("Unable to create button device because the 'Child Button' DTH is not installed", ex)
            }
        }
        runIn(5, executeConfigureCmds, [overwrite: true])
    }
    return []
}

def configure() {
    logDebug "configure()..."
    if (state.resyncAll == null) {
        state.resyncAll = true
        runIn(8, executeConfigureCmds, [overwrite: true])
    } else {
        if (!pendingChanges) {
            state.resyncAll = true
        }
        executeConfigureCmds()
    }
    return []
}

def executeConfigureCmds() {
    runIn(6, refreshSyncStatus)

    def cmds = []

    configParams.each { param ->
        def storedVal = getParamStoredValue(param.num)
        def paramVal = param.value
        if (state.resyncAll || ("${storedVal}" != "${paramVal}")) {
            cmds << secureCmd(zwave.configurationV1.configurationSet(parameterNumber: param.num, size: param.size, scaledConfigurationValue: paramVal))
            cmds << secureCmd(zwave.configurationV1.configurationGet(parameterNumber: param.num))
        }
    }

    state.resyncAll = false
    if (cmds) {
        sendHubCommand(new hubitat.device.HubMultiAction(delayBetween(cmds, 300), hubitat.device.Protocol.ZWAVE))
    }
    return []
}

def ping() {
    logDebug "ping()..."
    return [ switchBinaryGetCmd() ]
}

def on() {
    logDebug "on()..."
    return [ switchBinarySetCmd(0xFF) ]
}

def off() {
    logDebug "off()..."
    return [ switchBinarySetCmd(0x00) ]
}

def refresh() {
    logDebug "refresh()..."
    refreshSyncStatus()
    return [ switchBinaryGetCmd() ]
}

private switchBinaryGetCmd() {
    return secureCmd(zwave.switchBinaryV1.switchBinaryGet())
}

private switchBinarySetCmd(val) {
    return secureCmd(zwave.switchBinaryV1.switchBinarySet(switchValue: val))
}

private secureCmd(cmd) {
    try {
        if (getDataValue("zwaveSecurePairingComplete") == "true") {
            return zwaveSecureEncap(cmd.format())
        } else {
            return cmd.format()
        }
    } catch (ex) {
        log.error("secureCmd exception", ex)
        return cmd.format()
    }
}

def parse(String description) {
    def result = []
    try {
        def cmd = zwave.parse(description, commandClassVersions)
        if (cmd) {
            result += zwaveEvent(cmd)
        } else {
            log.warn "Unable to parse: $description"
        }
    } catch (e) {
        log.error "${e}"
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
    logTrace "SecurityMessageEncapsulation: ${cmd}"
    def encapsulatedCmd = cmd.encapsulatedCommand(commandClassVersions)
    def result = []
    if (encapsulatedCmd) {
        result += zwaveEvent(encapsulatedCmd)
    } else {
        log.warn "Unable to extract encapsulated cmd from $cmd"
    }
    return result
}

def zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    logTrace "ConfigurationReport: ${cmd}"
    sendEvent(name:  "syncStatus", value:  "Syncing...", displayed:  false)
    runIn(4, refreshSyncStatus)
    def param = configParams.find { it.num == cmd.parameterNumber }
    if (param) {
        def val = cmd.scaledConfigurationValue
        logDebug "${param.name}(#${param.num}) = ${val}"
        state["configVal${param.num}"] = val
    } else {
        logDebug "Parameter #${cmd.parameterNumber} = ${cmd.scaledConfigurationValue}"
    }
    return []
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
    logTrace "VersionReport: ${cmd}"
    def subVersion = String.format("%02d", cmd.applicationSubVersion)
    def fullVersion = "${cmd.applicationVersion}.${subVersion}"
    sendEvent(name:  "firmwareVersion", value:  fullVersion)
    return []
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    logTrace "BasicReport: ${cmd}"
    sendSwitchEvents(cmd.value, "physical")
    return []
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    logTrace "SwitchBinaryReport: ${cmd}"
    sendSwitchEvents(cmd.value, "digital")
    return []
}

private sendSwitchEvents(rawVal, type) {
    def switchVal = (rawVal == 0xFF) ? "on" : "off"
    sendEvent(name:  "switch", value: switchVal, displayed:  true, type:  type)
    if (isButtonAvailable() && type == "physical") {
        if (paddleControlParam.value == 2) {
            sendButtonEvent("pushed")
        } else {
            def paddlesReversed = (paddleControlParam.value == 1)
            def btnVal = ((rawVal && !paddlesReversed) || (!rawVal && paddlesReversed)) ? "up" : "down"
            sendButtonEvent(btnVal)
        }
    }
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    logDebug "Unhandled zwaveEvent: $cmd"
    return []
}

def refreshSyncStatus() {
    def changes = pendingChanges
    sendEvent(name:  "syncStatus", value:  (changes ?  "${changes} Pending Changes" : "Synced"), displayed:  false)
}

private static getCommandClassVersions() {
    [
        0x20: 1,	// Basic
        0x25: 1,	// Switch Binary
        0x5B: 1,	// CentralScene (3)
        0x55: 1,	// Transport Service
        0x59: 1,	// AssociationGrpInfo
        0x5A: 1,	// DeviceResetLocally
        0x27: 1,	// Switch All
        0x5E: 2,	// ZwaveplusInfo
        0x6C: 1,	// Supervision
        0x70: 1,	// Configuration
        0x7A: 2,	// FirmwareUpdateMd
        0x72: 2,	// ManufacturerSpecific
        0x73: 1,	// Powerlevel
        0x85: 2,	// Association
        0x86: 1,	// Version (2)
        0x8E: 2,	// Multi Channel Association
        0x98: 1,	// Security S0
        0x9F: 1		// Security S2
    ]
}

private getPendingChanges() {
    return configParams.count { "${it.value}" != "${getParamStoredValue(it.num)}" }
}

private getParamStoredValue(paramNum) {
    return safeToInt(state["configVal${paramNum}"] , null)
}

private getConfigParams() {
    return [
        ledModeParam,
        autoOffIntervalParam,
        autoOnIntervalParam,
        powerFailureRecoveryParam,
        paddleControlParam
    ]
}

private static getPaddleControlOptions() {
    return [
        "0":"Normal",
        "1":"Reverse",
        "2":"Toggle"
    ]
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
    logTrace "CentralSceneNotification: ${cmd}"
    if (state.lastSequenceNumber != cmd.sequenceNumber) {
        state.lastSequenceNumber = cmd.sequenceNumber
        logTrace "${cmd}"
        def paddle = (cmd.sceneNumber == 1) ? "down" : "up"
        def btnVal
        switch (cmd.keyAttributes) {
            case 0:
                btnVal = paddle
                break
            case 3:
                btnVal = paddle + "_2x"
                break
        }

        if (btnVal) {
            sendButtonEvent(btnVal)
        }
    }
    return []
}

private sendButtonEvent(value) {
    if (childDevices) {
        childDevices[0].sendEvent(name: "button", value: value, data:[buttonNumber: 1], isStateChange: true)
    }
}

private getPaddleControlParam() {
    def num = isButtonAvailable()? 1 : 1000
    return getParam(num, "Paddle Control", 1, 0, paddleControlOptions)
}

private getLedModeParam() {
    def num = isButtonAvailable()? 2 : 1
    return getParam(num, "LED Indicator Mode", 1, 0,  alternativeLedOptions)
}

private getAutoOffIntervalParam() {
    def num = isButtonAvailable()? 4 : 2
    return getParam(num, "Auto Turn-Off Timer(0, Disabled; 1 - 65535 minutes)", 4, 0, null, "0..65535")
}

private getAutoOnIntervalParam() {
    def num = isButtonAvailable()? 6 : 4
    return getParam(num, "Auto Turn-On Timer(0, Disabled; 1 - 65535 minutes)", 4, 0, null, "0..65535")
}

private getPowerFailureRecoveryParam() {
    def num = isButtonAvailable()? 8 : 6
    return getParam(num, "Power Failure Recovery", 1, 0, powerFailureRecoveryOptions)
}

private getParam(num, name, size, defaultVal, options = null, range = null) {
    def val = safeToInt((settings ? settings["configParam${num}"] : null), defaultVal)
    def map = [num: num, name: name, size: size, value: val]
    if (options) {
        map.valueName = options?.find { k, v -> "${k}" == "${val}" }?.value
        map.options = setDefaultOption(options, defaultVal)
    }
    if (range) {
        map.range = range
    }
    return map
}

private static setDefaultOption(options, defaultVal) {
    return options?.collect{ k, v ->
        if ("${k}" == "${defaultVal}") {
            v = "${v} [DEFAULT]"
        }
        ["$k": "$v"]
    }
}

private getAlternativeLedOptions() {
    if (isButtonAvailable()) {
        return [
                "0":"On When On",
                "1":"Off When On",
                "2":"Always Off"
        ]
    } else {
        return [
                "0":"Off When On",
                "1":"On When On",
                "2":"Always Off",
                "3":"Always On"
        ]
    }
}

private static getPowerFailureRecoveryOptions() {
    return [
        "0":"Turn Off",
        "1":"Turn On",
        "2":"Restore Last State"
    ]
}

private static safeToInt(val, defaultVal = 0) {
    return "${val}"?.isInteger() ? "${val}".toInteger() : defaultVal
}

private static isDuplicateCommand(lastExecuted, allowedMil) {
    !lastExecuted ? false : (lastExecuted + allowedMil > new Date().time)
}

private logDebug(msg) {
    log.debug "$msg"
}

private logTrace(msg) {
    log.trace "$msg"
}

private isButtonAvailable() {
    if (device == null) {
        log.error "isButtonAvailable device = null"
        return true
    } else {
        log.debug "isButtonAvailable device.rawDescription = ${device.rawDescription}"
        return "${device.rawDescription}".contains("model:EE01") || "${device.rawDescription}".contains("model:EE03") || "${device.rawDescription}".contains("model:A005") || "${device.rawDescription}".contains("model:BB01") || "${device.rawDescription}".contains("model:BB03")
    }
}