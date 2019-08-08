/**
 * Custom WS3000 Driver for a custom WS3000 server. Probably not very useful.
 *
 *  Copyright 2019 EpicVoyage
 *
 *  Based on the work of @mattw01, @Cobra, @Scottma61, and valuable input from the Hubitat community
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 */

metadata {
	definition (name: "Custom WS3000 Driver", namespace: "EpicVoyage", author: "EpicVoyage", importUrl: "https://raw.githubusercontent.com/EpicVoyage/ws3000-hubitat/master/ws3000.groovy") {
		capability "Sensor"
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"

		command "poll"
		command "ForcePoll"
 		command "ResetPollCount"
		attribute "observation_time", "string"
		attribute "feelsLike", "number"
		attribute "dewpoint", "number"
		attribute "temperatureUnit", "string"
 		attribute "DriverAuthor", "string"
		attribute "DriverVersion", "string"
		attribute "DriverStatus", "string"
		attribute "DriverUpdate", "string"
		attribute "humidity", "string"
	}

	preferences() {
		section("Query Inputs") {
			input "serverIp", "string", required: true, title: "Server Name/IP"
			input "serverPort", "number", required: false, title: "Server Port", defaultValue: "8080"
			input "pollLocation", "enum", required: true, title: "Sensor Number", defaultValue: "1", options: ["1", "2", "3", "4", "5", "6", "7", "8"]
			input "unitFormat", "enum", required: true, title: "Unit Format", defaultValue: "Imperial", options: ["Imperial", "Metric"]
			input "pollIntervalLimit", "number", title: "Poll Interval Limit:", required: true, defaultValue: 1
			input "autoPoll", "bool", required: false, title: "Enable Auto Poll", defaultValue: true
			input "pollInterval", "enum", title: "Auto Poll Interval:", required: false, defaultValue: "5 Minutes", options: ["1 Minute", "5 Minutes", "10 Minutes", "15 Minutes", "30 Minutes", "1 Hour", "3 Hours"]
			input "logSet", "bool", title: "Log All WS3000 Response Data", required: true, defaultValue: false
			input "cutOff", "time", title: "New Day Starts", required: true, defaultValue: "00:00"
		}
	}
}


def updated() {
	log.debug "updated called"
	updateCheck()
	unschedule()
	version()
	state.NumOfPolls = 0
	ForcePoll()
	def pollIntervalCmd = (settings?.pollInterval ?: "5 Minutes").replace(" ", "")
	if (autoPoll)
		"runEvery${pollIntervalCmd}"(pollSchedule)

	def changeOver = cutOff
	schedule(changeOver, ResetPollCount)
}

def ResetPollCount() {
	state.NumOfPolls = -1
	log.info "Poll counter reset.."
	ForcePoll()
}

def pollSchedule() {
	ForcePoll()
}

def parse(String description) {
}

def poll() {
	if (now() - (state.lastPoll ? state.lastPoll : 0) > (pollIntervalLimit * 60000))
		ForcePoll()
	else
		log.debug "Poll called before interval threshold was reached"
}

def formatUnit(){
	if(unitFormat == "Imperial"){
		state.unit = "e"
		log.info "state.unit = $state.unit"
	}
	if(unitFormat == "Metric"){
		state.unit = "m"
		log.info "state.unit = $state.unit"
	}
}

def ForcePoll(){
	poll1()
}

BigDecimal celsiusToFahrenheit(BigDecimal celsius) {
	BigDecimal ret = (celsius * 1.8) + 32;
	return ret.setScale(1, BigDecimal.ROUND_HALF_UP);
}

BigDecimal fahrenheitToCelsius(BigDecimal fahrenheit) {
	BigDecimal ret = (fahrenheit - 32) / 1.8;
	return ret.setScale(1, BigDecimal.ROUND_HALF_UP);
}

def poll1() {
	formatUnit()
	state.NumOfPolls = state.NumOfPolls ? (state.NumOfPolls) + 1 : 1
	log.info " state.NumOfPolls = $state.NumOfPolls"

	log.debug "WS3000: ForcePoll called"
	def params1 = [
		// Current Observation
		uri: "http://${serverIp}:${serverPort}/${pollLocation}"
	]

	try {
		httpGet(params1) { resp ->
			if(logSet == true){
				resp.headers.each {
					log.debug "Response1: ${it.name} : ${it.value}"
				}
				log.debug "params1: ${params1}"
				log.debug "response contentType: ${resp.contentType}"
 				log.debug "response data: ${resp.data}"
			}

			sendEvent(name: "pollsSinceReset", value: state.NumOfPolls)
			sendEvent(name: "stationID", value: pollLocation)

			sendEvent(name: "humidity", value: resp.data.humidity)

            if(unitFormat == "Imperial") {
				sendEvent(name: "temperature", value: resp.data.temperatureF, unit: "F")
				sendEvent(name: "feelsLike", value: resp.data.heatIndexF, unit: "F")
				sendEvent(name: "dewpoint", value: resp.data.dewPointF, unit: "F")
			}
			if(unitFormat == "Metric") {
				sendEvent(name: "temperature", value: resp.data.temperature, unit: "C")
				sendEvent(name: "feelsLike", value: fahrenheitToCelsius(resp.data.heatIndexF), unit: "C")
				sendEvent(name: "dewpoint", value: resp.data.dewPoint, unit: "C")
			}

			state.lastPoll = now()

            // Time manipulation is the most likely to fail. Do it last.
            long lastUpdateUTC = new BigDecimal(resp.data.lastUpdateUTC) * 1000
            sendEvent(name: "observation_time", value: new Date(lastUpdateUTC).timeString)
		}
	} catch (e) {
		log.error "something went wrong in Poll1: $e"
	}
}

def version(){
	updateCheck()
	// schedule("0 0 9 ? * FRI *", updateCheck)
}

def updateCheck(){
	setVersion()
}

def setVersion(){
	state.version = "1.0.2"
	state.InternalName = "WS3000Driver"
	sendEvent(name: "DriverAuthor", value: "EpicVoyage")
	sendEvent(name: "DriverVersion", value: state.version)
}
