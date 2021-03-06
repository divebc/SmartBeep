/**
 *  ThisisBeep is a SmartThings device driver (a "thing") for the Beep Dial media
 *  player. 
 *
 *  --------------------------------------------------------------------------
 *
 *  Based on the original SmarthThings Sonos Code, and branched from the 
 *  VLC Thing player.
 *
 *  Please visit <https://github.com/statusbits/smartthings-vlc/> for
 *  more information on the VLC Player
 *
 *  --------------------------------------------------------------------------
 *
 *  Copyright (c) 2015 michel@labelles.ca
 *
 *  This program is free software: you can redistribute it and/or modify it
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation, either version 3 of the License, or (at your option)
 *  any later version.
 *
 *  This program is distributed in the hope that it will be useful, but
 *  WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 *  or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 *  for more details.
 *
 *  You should have received a copy of the GNU General Public License along
 *  with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  --------------------------------------------------------------------------
 *
 *  The latest version of this file can be found at:
 *  https://github.com/divebc/smartthings-beep/
 *
 *  Version 1.1.0 (2014-11-03)
 */

/* Note code as published is not fuctional at this time.... Early development effort
*/


import groovy.json.JsonSlurper

preferences {
    input("confIpAddr", "string", title:"Enter BEEP IP address",
        required:true, displayDuringSetup: true)
    input("confTcpPort", "number", title:"Enter BEEP TCP port",
        defaultValue:"80", required:true, displayDuringSetup:true)
    input("confPassword", "password", title:"Enter your BEEP password",
        required:false, displayDuringSetup:true)
}

metadata {
    definition (name:"BEEP Thing", namespace:"BeepDial", author:"michel@labelles.ca") {
        capability "Actuator"
        capability "Switch"
        capability "Music Player"
        capability "Speech Synthesis"
        capability "Refresh"
        capability "Polling"

        // Custom attributes
        attribute "savedVolume", "string"

        // Custom commands
        command "enqueue", ["string"]
        command "seek", ["number"]
        command "playTrackAndResume", ["string","number","number"]
        command "playTrackAndRestore", ["string","number","number"]
        command "playTextAndResume", ["string","number"]
        command "playTextAndRestore", ["string","number"]
        command "playSoundAndTrack", ["string","number","json_object","number"]
        command "__testTTS"
    }

    tiles {
        standardTile("main", "device.status", canChangeIcon:true) {
            state "disconnected", label:'Connect', icon:"st.Electronics.electronics16", backgroundColor:"#FFCC00", action:"refresh.refresh"
            state "stopped", label:'Stopped', icon:"st.Electronics.electronics16", nextState:"playing", backgroundColor:"#ffffff", action:"Music Player.play"
            state "paused", label:'Paused', icon:"st.Electronics.electronics16", nextState:"playing", backgroundColor:"#ffffff", action:"Music Player.play"
            state "playing", label:'Playing', icon:"st.Electronics.electronics16", nextState:"paused", backgroundColor:"#79b821", action:"Music Player.pause"
        }

        standardTile("play", "device.status", inactiveLabel:false, decoration:"flat") {
            state "stopped", label:'', icon:"st.sonos.play-btn", nextState:"playing", action:"Music Player.play"
            state "paused", label:'', icon:"st.sonos.play-btn", nextState:"playing", action:"Music Player.play"
            state "playing", label:'', icon:"st.sonos.pause-btn", nextState:"paused", action:"Music Player.pause"
        }

        standardTile("stop", "device.status", inactiveLabel:false, decoration:"flat") {
            state "stopped", label:'', icon:"st.sonos.play-btn", nextState:"playing", action:"Music Player.play"
            state "paused", label:'', icon:"st.sonos.stop-btn", nextState:"stopped", action:"Music Player.stop"
            state "playing", label:'', icon:"st.sonos.stop-btn", nextState:"stopped", action:"Music Player.stop"
        }

        standardTile("nextTrack", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'', icon:"st.sonos.next-btn", action:"Music Player.nextTrack"
        }

        standardTile("previousTrack", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:'', icon:"st.sonos.previous-btn", action:"music Player.previousTrack"
        }

        standardTile("mute", "device.mute", inactiveLabel:false, decoration:"flat") {
            state "unmuted", label:"Mute", icon:"st.custom.sonos.unmuted", action:"Music Player.mute"
            state "muted", label:"Unmute", icon:"st.custom.sonos.muted", action:"Music Player.unmute"
        }

        controlTile("volume", "device.level", "slider", height:1, width:3, inactiveLabel:false) {
            state "level", action:"Music Player.setLevel"
        }

        valueTile("nowPlaying", "device.trackDescription", height:1, width:3, inactiveLabel:true, decoration:"flat") {
            state "default", label:'${currentValue}'
        }

        standardTile("refresh", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", icon:"st.secondary.refresh", action:"refresh.refresh"
        }

        standardTile("testAudio", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:"Audio Test", action:"__testAudio"
        }

        standardTile("testTTS", "device.status", inactiveLabel:false, decoration:"flat") {
            state "default", label:"Test", icon:"http://statusbits.github.io/icons/vlcthing.png", action:"__testTTS"
        }

        main(["main"])

        details([
            "nowPlaying",
            "previousTrack", "play", "nextTrack",
            "mute", "stop", "refresh",
            "volume",
            "testTTS"
        ])
    }

    simulator {
        status "Stoped"         : "simulator:true, state:'stopped'"
        status "Playing"        : "simulator:true, state:'playing'"
        status "Paused"         : "simulator:true, state:'paused'"
        status "Volume 0%"      : "simulator:true, volume:0"
        status "Volume 25%"     : "simulator:true, volume:127"
        status "Volume 50%"     : "simulator:true, volume:255"
        status "Volume 75%"     : "simulator:true, volume:383"
        status "Volume 100%"    : "simulator:true, volume:511"
    }
}

def parse(String message) {
    def msg = stringToMap(message)
    if (msg.containsKey("simulator")) {
        // simulator input
        return parseHttpResponse(msg)
    }

    if (!msg.containsKey("headers")) {
        log.error "No HTTP headers found in '${message}'"
        return null
    }

    // parse HTTP response headers
    def headers = new String(msg.headers.decodeBase64())
    def parsedHeaders = parseHttpHeaders(headers)
    //log.debug "parsedHeaders: ${parsedHeaders}"
    if (parsedHeaders.status != 200) {
        log.error "Server error: ${parsedHeaders.reason}"
        return null
    }

    // parse HTTP response body
    if (!msg.body) {
        log.error "No HTTP body found in '${message}'"
        return null
    }

    def body = new String(msg.body.decodeBase64())
    //log.debug "body: ${body}"
    def slurper = new JsonSlurper()
    return parseHttpResponse(slurper.parseText(body))
}

// switch.on
def on() {
    play()
}

// switch.off
def off() {
    stop()
}

// MusicPlayer.play
def play() {
    TRACE("play()")

    def command
    if (device.currentValue('status') == 'paused') {
        command = 'command=pl_forceresume'
    } else {
        command = 'command=pl_play'
    }

    return beepCommand(command, 500)
}

// MusicPlayer.stop
def stop() {
    TRACE("stop()")

    return beepCommand("command=pl_stop", 500)
}

// MusicPlayer.pause
def pause() {
    TRACE("pause()")

    return beepCommand("command=pl_forcepause")
}

// MusicPlayer.playTrack
def playTrack(uri) {
    TRACE("playTrack(${uri})")

    def command = "command=in_play&input=" + URLEncoder.encode(uri, "UTF-8")
    return beepCommand(command, 500)
}

// MusicPlayer.playText
def playText(text) {
    TRACE("playText(${text})")

    def sound = textToSpeech(text, true)
    return playTrack(sound.uri)
}

// MusicPlayer.setTrack
def setTrack(name) {
    TRACE("setTrack(${name}) not implemented")
    return null
}

// MusicPlayer.resumeTrack
def resumeTrack(name) {
    TRACE("resumeTrack(${name}) not implemented")
    return null
}

// MusicPlayer.restoreTrack
def restoreTrack(name) {
    TRACE("restoreTrack(${name}) not implemented")
    return null
}

// MusicPlayer.nextTrack
def nextTrack() {
    TRACE("nextTrack()")

    return beepCommand("command=pl_next", 500)
}

// MusicPlayer.previousTrack
def previousTrack() {
    TRACE("previousTrack()")

    return beepCommand("command=pl_previous", 500)
}

// MusicPlayer.setLevel
def setLevel(number) {
    TRACE("setLevel(${number})")

    if (device.currentValue('mute') == 'muted') {
        sendEvent(name:'mute', value:'unmuted')
    }

    sendEvent(name:"level", value:number)
    def volume = ((number * 512) / 100) as int
    return beepCommand("command=volume&val=${volume}")
}

// MusicPlayer.mute
def mute() {
    TRACE("mute()")

    if (device.currentValue('mute') == 'muted') {
        return null
    }

    sendEvent(name:'savedVolume', value:device.currentValue('level'), displayed:false)
    sendEvent(name:'mute', value:'muted')
    sendEvent(name:'level', value:0)
    return beepCommand("command=volume&val=0")
}

// MusicPlayer.unmute
def unmute() {
    TRACE("unmute()")

    if (device.currentValue('mute') == 'muted') {
        return setLevel(device.currentValue('savedVolume').toInteger())
    }

    return null
}

// SpeechSynthesis.speak
def speak(text) {
    TRACE("speak(${text})")

    def sound = textToSpeech(text, true)
    return playTrack(sound.uri)
}

// polling.poll 
def poll() {
    TRACE("poll()")
    return refresh()
}

// refresh.refresh
def refresh() {
    TRACE("refresh()")
    STATE()

    setNetworkId(settings.confIpAddr, settings.confTcpPort)
    if (device.currentValue('status') == null) {
        setDefaults()
    }

    return beepGetStatus()
}

def enqueue(uri) {
    TRACE("enqueue(${uri})")
    def command = "command=in_enqueue&input=" + URLEncoder.encode(uri, "UTF-8")
    return beepCommand(command)
}

def seek(trackNumber) {
    TRACE("seek(${trackNumber})")
    def command = "command=pl_play&id=${trackNumber}"
    return beepCommand(command, 500)
}

def playTrackAndResume(uri, duration, volume = null) {
    TRACE("playTrackAndResume(${uri}, ${duration}, ${volume})")

    // FIXME
    return playTrackAndRestore(uri, duration, volume)
}

def playTrackAndRestore(uri, duration, volume = null) {
    TRACE("playTrackAndRestore(${uri}, ${duration}, ${volume})")

    def currentStatus = device.currentValue('status')
    def currentVolume = device.currentValue('level')
    def currentMute = device.currentValue('mute')
    def actions = []
    if (currentStatus == 'playing') {
        actions << beepCommand("command=pl_stop")
        actions << delayHubAction(500)
    }

    if (volume) {
        actions << setLevel(volume)
        actions << delayHubAction(500)
    } else if (currentMute == 'muted') {
        actions << unmute()
        actions << delayHubAction(500)
    }

    actions << playTrack(uri)
    actions << delayHubAction((duration + 1) * 1000)
    actions << beepCommand("command=pl_stop")
    actions << delayHubAction(500)

    if (currentMute == 'muted') {
        actions << mute()
    } else if (volume) {
        actions << setLevel(currentVolume)
    }

    actions << beepGetStatus()
    actions = actions.flatten()
    //log.debug "actions: ${actions}"

    return actions
}

def playTextAndResume(text, volume = null) {
    TRACE("playTextAndResume(${text}, ${volume})")

    def sound = textToSpeech(text, true)
    return playTrackAndResume(sound.uri, (sound.duration as Integer) + 1, volume)
}

def playTextAndRestore(text, volume = null) {
    TRACE("playTextAndRestore(${text}, ${volume})")

    def sound = textToSpeech(text, true)
    return playTrackAndRestore(sound.uri, (sound.duration as Integer) + 1, volume)
}

def playSoundAndTrack(uri, duration, trackData, volume = null) {
    TRACE("playSoundAndTrack(${uri}, ${duration}, ${trackData}, ${volume})")

    // FIXME
    return playTrackAndRestore(uri, duration, volume)
}

def __testTTS() {
    TRACE("__testTTS()")
    def text = "beep for Smart Things is brought to you by Michel Labelle"
    return playTextAndResume(text)
}

// Sets device Network ID in 'AAAAAAAA:PPPP' format
private String setNetworkId(ipaddr, port) {
    TRACE("setNetworkId(${ipaddr}, ${port})")

    def hexIp = ipaddr.tokenize('.').collect {
        String.format('%02X', it.toInteger())
    }.join()

    def hexPort = String.format('%04X', port.toInteger())
    device.deviceNetworkId = "${hexIp}:${hexPort}"
    log.debug "device.deviceNetworkId = ${device.deviceNetworkId}"
}

private beepGet(String path) {
    TRACE("beepGet(${path})")

    def headers = [
        HOST:       getHostAddress(),
        Accept:     "*/*"
    ]

    if (settings.confPassword) {
        def auth = ":${settings.confPassword}".bytes.encodeBase64()
        headers['Authorization'] = "Basic ${auth}"
    }

    def httpRequest = [
        method:     'GET',
        path:       path,
        headers:    headers
    ]

    //log.debug "httpRequest: ${httpRequest}"
    return new physicalgraph.device.HubAction(httpRequest)
}

private def delayHubAction(ms) {
    TRACE("delayHubAction(${ms})")
    return new physicalgraph.device.HubAction("delay ${ms}")
}

private beepGetStatus() {
    return beepGet("/requests/status.json")
}

private beepCommand(command, refresh = 0) {
    TRACE("beepCommand(${command})")

    def actions = [
        beepGet("/requests/status.json?${command}")
    ]

    if (refresh) {
        actions << delayHubAction(refresh)
        actions << beepGetStatus()
    }

    return actions
}

private def beepGetPlaylists() {
    TRACE("getPlaylists()")
    return beepGet("/requests/playlist.json")
}

private parseHttpHeaders(String headers) {
    def lines = headers.readLines()
    def status = lines.remove(0).split()

    def result = [
        protocol:   status[0],
        status:     status[1].toInteger(),
        reason:     status[2]
    ]

    return result
}

private def parseHttpResponse(Map data) {
    TRACE("parseHttpResponse(${data})")

    def events = []

    if (data.containsKey('state')) {
        def beepState = data.state
        //TRACE("beep state: ${beepState})")
        events << createEvent(name:"status", value:beepState)
        if (beepState == 'stopped') {
            events << createEvent([name:'trackDescription', value:''])
        }
    }

    if (data.containsKey('volume')) {
        //TRACE("beep volume: ${data.volume})")
        def volume = ((data.volume.toInteger() * 100) / 512) as int
        events << createEvent(name:'level', value:volume)
    }

    if (data.containsKey('information')) {
        parseTrackInfo(events, data.information)
    }

    //log.debug "events: ${events}"
    return events
}

private def parseTrackInfo(events, Map info) {
    //TRACE("parseTrackInfo(${events}, ${info})")

    if (info.containsKey('category') && info.category.containsKey('meta')) {
        def meta = info.category.meta
        TRACE("Track info: ${meta})")
        if (meta.containsKey('filename')) {
            if (meta.filename.contains("//s3.amazonaws.com/smartapp-")) {
                log.trace "Skipping event generation for sound file ${meta.filename}"
                return
            }
        }

        def track = ""
        if (meta.containsKey('artist')) {
            track = "${meta.artist} - "
        }
        if (meta.containsKey('title')) {
            track += meta.title
        } else if (meta.containsKey('filename')) {
            def parts = meta.filename.tokenize('/');
            track += parts.last()
        } else {
            track += '<untitled>'
        }

        if (track != device.currentState('trackDescription')) {
            meta.station = track
            events << createEvent(name:'trackDescription', value:track, displayed:false)
            events << createEvent(name:'trackData', value:meta.encodeAsJSON(), displayed:false)
        }
    }
}

private def setDefaults() {
    TRACE("setDefaults()")

    def events = []

    if (device.currentValue('status') == null) {
        events << createEvent([name:'status', value:'disconnected', displayed:false])
    }

    if (device.currentValue('level') == null) {
        events << createEvent([name:'level', value:'0', displayed:false])
    }

    if (device.currentValue('mute') == null) {
        events << createEvent([name:'mute', value:'unmuted', displayed:false])
    }

    if (device.currentValue('trackDescription') == null) {
        events << createEvent([name:'trackDescription', value:'', displayed:false])
    }

    events.each {
        sendEvent(it)
    }
}

private def getHostAddress() {
    return "${settings.confIpAddr}:${settings.confTcpPort}"
}

private def TRACE(message) {
    //log.debug message
}

private def STATE() {
    log.debug "deviceNetworkId: ${device.deviceNetworkId}"
    log.debug "status: ${device.currentValue('status')}"
    log.debug "level: ${device.currentValue('level')}"
    log.debug "mute: ${device.currentValue('mute')}"
    log.debug "trackDescription: ${device.currentValue('trackDescription')}"
}