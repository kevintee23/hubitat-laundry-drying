/**
 * Ecowitt Virtual Sensor v1.1
 *
 * Virtual device to store Ecowitt weather station data.
 * Compatible with webCoRE for setting values.
 */

metadata {
  definition(name: "Ecowitt Virtual Sensor", namespace: "kevintee", author: "Kevin Tee") {
    capability "TemperatureMeasurement"
    capability "RelativeHumidityMeasurement"
    capability "IlluminanceMeasurement"
    capability "Sensor"

    // Custom attributes for wind and rain
    attribute "wind_speed", "number"
    attribute "wind_gust", "number"
    attribute "wind_direction", "number"
    attribute "rain_rate", "number"
    attribute "rain_daily", "number"
    attribute "uv_index", "number"
    attribute "pressure", "number"
    attribute "dew_point", "number"
    attribute "feels_like", "number"
    attribute "lastUpdate", "string"

    // webCoRE-friendly commands (simple single parameter)
    command "setTemperature", [[name: "value", type: "NUMBER"]]
    command "setHumidity", [[name: "value", type: "NUMBER"]]
    command "setIlluminance", [[name: "value", type: "NUMBER"]]
    command "setWindSpeed", [[name: "value", type: "NUMBER"]]
    command "setWindGust", [[name: "value", type: "NUMBER"]]
    command "setWindDirection", [[name: "value", type: "NUMBER"]]
    command "setRainRate", [[name: "value", type: "NUMBER"]]
    command "setRainDaily", [[name: "value", type: "NUMBER"]]
    command "setUvIndex", [[name: "value", type: "NUMBER"]]
    command "setPressure", [[name: "value", type: "NUMBER"]]
    command "setDewPoint", [[name: "value", type: "NUMBER"]]
    command "setFeelsLike", [[name: "value", type: "NUMBER"]]
  }

  preferences {
    input "enableDebug", "bool", title: "Enable debug logging", defaultValue: false
  }
}

def installed() {
  log.info "Ecowitt Virtual Sensor installed"
}

def updated() {
  log.info "Ecowitt Virtual Sensor updated"
}

// Standard capability attributes
def setTemperature(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setTemperature: ${v}"
  sendEvent(name: "temperature", value: v, unit: "°C")
  updateTimestamp()
}

def setHumidity(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setHumidity: ${v}"
  sendEvent(name: "humidity", value: v, unit: "%")
  updateTimestamp()
}

def setIlluminance(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setIlluminance: ${v}"
  sendEvent(name: "illuminance", value: v, unit: "lux")
  updateTimestamp()
}

// Wind attributes
def setWindSpeed(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setWindSpeed: ${v}"
  sendEvent(name: "wind_speed", value: v, unit: "km/h")
  updateTimestamp()
}

def setWindGust(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setWindGust: ${v}"
  sendEvent(name: "wind_gust", value: v, unit: "km/h")
  updateTimestamp()
}

def setWindDirection(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setWindDirection: ${v}"
  sendEvent(name: "wind_direction", value: v, unit: "°")
  updateTimestamp()
}

// Rain attributes
def setRainRate(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setRainRate: ${v}"
  sendEvent(name: "rain_rate", value: v, unit: "mm/hr")
  updateTimestamp()
}

def setRainDaily(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setRainDaily: ${v}"
  sendEvent(name: "rain_daily", value: v, unit: "mm")
  updateTimestamp()
}

// Other attributes
def setUvIndex(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setUvIndex: ${v}"
  sendEvent(name: "uv_index", value: v)
  updateTimestamp()
}

def setPressure(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setPressure: ${v}"
  sendEvent(name: "pressure", value: v, unit: "inHg")
  updateTimestamp()
}

def setDewPoint(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setDewPoint: ${v}"
  sendEvent(name: "dew_point", value: v, unit: "°C")
  updateTimestamp()
}

def setFeelsLike(value) {
  if (value == null) return
  BigDecimal v = value as BigDecimal
  if (enableDebug) log.debug "setFeelsLike: ${v}"
  sendEvent(name: "feels_like", value: v, unit: "°C")
  updateTimestamp()
}

private void updateTimestamp() {
  sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))
}
