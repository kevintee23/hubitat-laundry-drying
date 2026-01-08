/**
 * Laundry Drying Progress Device v7.4
 *
 * Virtual device created and managed by the Laundry Drying Progress App.
 * Do not install this device manually - use the app instead.
 *
 * Features:
 * - Uses dew point depression for more accurate drying prediction
 * - Drying location affects sun/wind benefit
 * - Optional custom lat/long for Open-Meteo fallback
 * - User-friendly dashboard attributes
 * - Learning: "Mark Dry" button learns from actual drying times to improve predictions
 */

import groovy.transform.Field

metadata {
  definition(name: "Laundry Drying Progress Device", namespace: "kevintee", author: "Kevin Tee") {
    capability "Switch"
    capability "SwitchLevel"
    capability "Actuator"
    capability "Sensor"
    capability "Refresh"
    capability "RelativeHumidityMeasurement"

    // Core attributes
    attribute "etaMinutes", "number"
    attribute "evapPower", "number"
    attribute "ratePerHour", "number"
    attribute "status", "string"
    attribute "reason", "string"
    attribute "weatherStatus", "string"
    attribute "lastUpdate", "string"
    attribute "debug", "string"
    attribute "dewPointDepression", "number"
    attribute "dryingLocation", "string"

    // User-friendly display attributes
    attribute "progress", "string"           // "65% dry"
    attribute "etaDisplay", "string"         // "2h 15m" or "Done"
    attribute "estimatedDoneTime", "string"  // "3:45 PM"
    attribute "timeElapsed", "string"        // "1h 30m"
    attribute "conditions", "string"         // "25°C, 52% RH, light breeze"
    attribute "startedAt", "string"          // "1:15 PM"

    // Learning/calibration attributes
    attribute "calibrationFactor", "number"  // Current calibration multiplier (1.0 = no adjustment)
    attribute "learningStatus", "string"     // "Learning (3 sessions)" or "Calibrated"

    command "reset"
    command "startNow"
    command "pauseNow"
    command "markDry"         // User presses when laundry is actually dry
    command "resetCalibration"  // Reset learning data to start fresh
  }

  preferences {
    input name: "customLocationInfo", type: "paragraph", title: "<b>Custom Location (for Open-Meteo)</b>",
      description: "Override the hub's location for weather data. Leave blank to use hub location. Useful if your laundry drying spot is at a different location than your hub."

    input name: "customLatitude", type: "decimal", title: "Latitude",
      description: "e.g. -33.8688 for Sydney. Leave blank to use hub location.",
      required: false, range: "-90..90"

    input name: "customLongitude", type: "decimal", title: "Longitude",
      description: "e.g. 151.2093 for Sydney. Leave blank to use hub location.",
      required: false, range: "-180..180"

    input name: "locationNote", type: "paragraph", title: "",
      description: "<small>Tip: Find coordinates at <a href='https://www.latlong.net/' target='_blank'>latlong.net</a></small>"
  }
}

/* ===================== CONSTANTS ===================== */

@Field static final Map SPEED_TO_HOURS = [
  "Fast": 3.0,
  "Normal": 4.5,
  "Slow": 6.5
]

// Drying location modifiers for sun and wind
@Field static final Map LOCATION_SUN_FACTOR = [
  "direct_sun": 1.0,      // Full sun benefit
  "partial_shade": 0.5,   // Half sun benefit
  "full_shade": 0.15,     // Minimal sun (ambient light only)
  "indoor": 0.0           // No sun benefit
]

@Field static final Map LOCATION_WIND_FACTOR = [
  "direct_sun": 1.0,      // Full wind benefit
  "partial_shade": 0.9,   // Slight reduction
  "full_shade": 0.7,      // Reduced airflow under cover
  "indoor": 0.3           // Limited indoor airflow
]

/* ===================== LIFECYCLE ===================== */

def installed() {
  log.info "Laundry Drying Progress Device installed"
  initialize()
}

def updated() {
  log.info "Laundry Drying Progress Device updated"
  initialize()
}

def initialize() {
  if (device.currentValue("level") == null) sendEvent(name: "level", value: 0)
  syncWetness()

  if (state.rateEma == null) state.rateEma = 0.0
  if (state.lastSuccessMs == null) state.lastSuccessMs = 0L
  if (state.startedAtMs == null) state.startedAtMs = 0L
  if (state.settings == null) state.settings = [dryingSpeed: "Normal", dryingLocation: "direct_sun", updateMinutes: 5, enableDebug: false]

  // Learning/calibration state (persists across sessions)
  if (state.calibrationFactor == null) state.calibrationFactor = 1.0
  if (state.sessionCount == null) state.sessionCount = 0
  state.initialEtaMinutes = null  // Reset each session

  sendEvent(name: "switch", value: "off")
  sendEvent(name: "status", value: "Ready")
  sendEvent(name: "reason", value: "—")
  sendEvent(name: "weatherStatus", value: "OK")
  sendEvent(name: "dryingLocation", value: "—")
  sendEvent(name: "progress", value: "0% dry")
  sendEvent(name: "etaDisplay", value: "—")
  sendEvent(name: "estimatedDoneTime", value: "—")
  sendEvent(name: "timeElapsed", value: "—")
  sendEvent(name: "conditions", value: "—")
  sendEvent(name: "startedAt", value: "—")

  // Update learning/calibration display
  updateLearningDisplay()
}

// Called by parent app to get custom location settings
def getLocationSettings() {
  return [
    latitude: settings.customLatitude,
    longitude: settings.customLongitude
  ]
}

// Called by parent app to update settings
def updateSettings(Map newSettings) {
  state.settings = newSettings
  if (newSettings.enableDebug) log.debug "Settings updated: ${newSettings}"

  // Update drying location display
  String locDisplay = [
    "direct_sun": "Direct sun",
    "partial_shade": "Partial shade",
    "full_shade": "Full shade",
    "indoor": "Indoor"
  ][newSettings.dryingLocation] ?: "Unknown"
  sendEvent(name: "dryingLocation", value: locDisplay)
}

/* ===================== COMMANDS ===================== */

def on() { startNow() }
def off() { pauseNow() }

def startNow() {
  // Reset level to 0 for a fresh start
  sendEvent(name: "level", value: 0)
  sendEvent(name: "progress", value: "0% dry")
  syncWetness()

  state.rateEma = 0.0
  state.startedAtMs = now()
  state.initialEtaMinutes = null  // Will be captured on first tick

  sendEvent(name: "switch", value: "on")
  sendEvent(name: "status", value: "Drying")
  sendEvent(name: "reason", value: "Starting…")
  sendEvent(name: "startedAt", value: new Date().format("h:mm a", location.timeZone))
  sendEvent(name: "timeElapsed", value: "0m")

  tick()
}

def pauseNow() {
  sendEvent(name: "switch", value: "off")
  sendEvent(name: "status", value: "Paused")
  sendEvent(name: "reason", value: "—")
  sendEvent(name: "etaDisplay", value: "Paused")
  sendEvent(name: "estimatedDoneTime", value: "—")
}

def markDry() {
  // User indicates laundry is actually dry - use this to calibrate future predictions
  if (device.currentValue("switch") != "on") {
    log.warn "markDry called but drying is not active"
    return
  }

  long startMs = (state.startedAtMs ?: 0L) as long
  if (startMs <= 0L) {
    log.warn "markDry called but no start time recorded"
    finishDone()
    return
  }

  // Calculate actual elapsed time in minutes
  long elapsedMs = now() - startMs
  BigDecimal actualMinutes = (elapsedMs / 60000.0) as BigDecimal

  // Get the initial predicted ETA (captured on first tick)
  BigDecimal predictedMinutes = (state.initialEtaMinutes ?: 0) as BigDecimal

  if (predictedMinutes > 0 && actualMinutes > 5) {
    // Calculate this session's calibration factor
    // If actual > predicted, factor > 1 (predictions were too optimistic)
    // If actual < predicted, factor < 1 (predictions were too pessimistic)
    BigDecimal thisSessionFactor = actualMinutes / predictedMinutes

    // Clamp to reasonable range (0.33x to 3.0x)
    thisSessionFactor = clamp(thisSessionFactor, 0.33, 3.0)

    // Update calibration using exponential moving average
    // 30% weight on new data = gradual learning
    BigDecimal oldCal = (state.calibrationFactor ?: 1.0) as BigDecimal
    BigDecimal newCal = (oldCal * 0.7) + (thisSessionFactor * 0.3)
    newCal = clamp(newCal, 0.33, 3.0)

    state.calibrationFactor = newCal
    state.sessionCount = ((state.sessionCount ?: 0) as Integer) + 1

    log.info "Learning: actual=${actualMinutes.setScale(0, BigDecimal.ROUND_HALF_UP)}min, predicted=${predictedMinutes.setScale(0, BigDecimal.ROUND_HALF_UP)}min, factor=${thisSessionFactor.setScale(2, BigDecimal.ROUND_HALF_UP)}, newCal=${newCal.setScale(2, BigDecimal.ROUND_HALF_UP)}, sessions=${state.sessionCount}"

    updateLearningDisplay()
  } else {
    log.info "markDry: skipping calibration (predicted=${predictedMinutes}, actual=${actualMinutes})"
  }

  // Finish the drying session
  finishDone()
}

def resetCalibration() {
  log.info "Resetting calibration data"
  state.calibrationFactor = 1.0
  state.sessionCount = 0
  updateLearningDisplay()
}

def reset() {
  sendEvent(name: "level", value: 0)
  syncWetness()
  sendEvent(name: "etaMinutes", value: null)
  sendEvent(name: "evapPower", value: null)
  sendEvent(name: "ratePerHour", value: null)
  sendEvent(name: "dewPointDepression", value: null)
  sendEvent(name: "debug", value: null)
  sendEvent(name: "lastUpdate", value: null)
  sendEvent(name: "status", value: "Ready")
  sendEvent(name: "reason", value: "—")
  sendEvent(name: "weatherStatus", value: "OK")
  sendEvent(name: "progress", value: "0% dry")
  sendEvent(name: "etaDisplay", value: "—")
  sendEvent(name: "estimatedDoneTime", value: "—")
  sendEvent(name: "timeElapsed", value: "—")
  sendEvent(name: "conditions", value: "—")
  sendEvent(name: "startedAt", value: "—")
  state.rateEma = 0.0
  state.lastSuccessMs = 0L
  state.startedAtMs = 0L
  state.initialEtaMinutes = null  // Reset session ETA, but keep calibration

  // Refresh learning display (calibration persists across resets)
  updateLearningDisplay()
}

def refresh() { tick() }

/* ===================== HEALTH CHECK ===================== */

def healthCheck() {
  if (device.currentValue("switch") != "on") return

  long last = (state.lastSuccessMs ?: 0L) as long
  if (last <= 0L) return

  Integer mins = (state.settings?.updateMinutes ?: 5) as Integer
  long thresholdMs = Math.max(2L, mins * 3L) * 60000L
  long ageMs = now() - last

  if (ageMs > thresholdMs) {
    sendEvent(name: "weatherStatus", value: "STALE")
    sendEvent(name: "status", value: "Warning: Stale data")
    sendEvent(name: "reason", value: "No successful update for ~${Math.round(ageMs / 60000.0)} min")
  }
}

/* ===================== CORE LOOP ===================== */

def tick() {
  if (device.currentValue("switch") != "on") return

  // Get weather data from parent app
  Map wx = parent.getWeatherData()

  if (!wx.ok) {
    sendEvent(name: "weatherStatus", value: wx.status ?: "ERROR")
    sendEvent(name: "status", value: wx.statusText ?: "Error")
    sendEvent(name: "reason", value: wx.reason ?: "Missing weather data")
    sendEvent(name: "conditions", value: "Error fetching data")
    return
  }

  // Update conditions display
  updateConditionsDisplay(wx)

  // Calculate dew point depression (how much the air can absorb moisture)
  BigDecimal dewPointDepression = null
  if (wx.dewPoint != null && wx.temp != null) {
    dewPointDepression = (wx.temp as BigDecimal) - (wx.dewPoint as BigDecimal)
    sendEvent(name: "dewPointDepression", value: dewPointDepression.setScale(1, BigDecimal.ROUND_HALF_UP), unit: "°C")
  }

  String dryingLocation = wx.dryingLocation ?: state.settings?.dryingLocation ?: "direct_sun"

  BigDecimal evap = calcEvap(
    wx.temp as BigDecimal,
    wx.rh as BigDecimal,
    wx.windMs as BigDecimal,
    wx.lux as BigDecimal,
    wx.rainRate as BigDecimal,
    dewPointDepression,
    dryingLocation
  )
  sendEvent(name: "evapPower", value: evap.setScale(3, BigDecimal.ROUND_HALF_UP))

  String speedSetting = state.settings?.dryingSpeed ?: "Normal"
  BigDecimal presetH = (SPEED_TO_HOURS[speedSetting] ?: 4.5) as BigDecimal
  BigDecimal rate = (evap / 0.70) * (1.0 / presetH)

  BigDecimal ema = (state.rateEma ?: 0.0) as BigDecimal
  BigDecimal rateEma = (ema < rate * 0.5) ? rate : (ema * 0.75 + rate * 0.25)
  state.rateEma = rateEma

  sendEvent(name: "ratePerHour", value: rate.setScale(4, BigDecimal.ROUND_HALF_UP))

  Integer lvl = (device.currentValue("level") ?: 0) as Integer
  BigDecimal mins = (state.settings?.updateMinutes ?: 5) as BigDecimal
  Integer add = (rate * (mins / 60.0) * 100.0).setScale(0, BigDecimal.ROUND_HALF_UP).intValue()

  Integer newLvl = clampInt(lvl + add, 0, 100)
  sendEvent(name: "level", value: newLvl)
  sendEvent(name: "progress", value: "${newLvl}% dry")
  syncWetness()

  // Calculate raw ETA (before calibration)
  Integer rawEta = (rateEma > 0.0001)
    ? (Math.ceil(((100 - newLvl) / 100.0) / rateEma * 60.0) as Integer)
    : 9999

  // Capture initial ETA prediction on first tick (for learning comparison)
  if (state.initialEtaMinutes == null && rawEta < 9999 && newLvl < 10) {
    state.initialEtaMinutes = rawEta
    if (state.settings?.enableDebug) log.debug "Captured initial ETA: ${rawEta} minutes"
  }

  // Apply calibration factor to ETA
  BigDecimal calFactor = (state.calibrationFactor ?: 1.0) as BigDecimal
  Integer eta = (rawEta < 9999) ? (Math.ceil(rawEta * calFactor) as Integer) : 9999

  sendEvent(name: "etaMinutes", value: eta)
  sendEvent(name: "etaDisplay", value: formatDuration(eta))

  // Calculate estimated done time
  if (eta < 9999) {
    Date doneTime = new Date(now() + (eta * 60000L))
    sendEvent(name: "estimatedDoneTime", value: doneTime.format("h:mm a", location.timeZone))
  } else {
    sendEvent(name: "estimatedDoneTime", value: "Unknown")
  }

  // Update time elapsed
  updateTimeElapsed()

  state.lastSuccessMs = now()
  sendEvent(name: "weatherStatus", value: "OK")
  sendEvent(name: "lastUpdate", value: new Date().format("yyyy-MM-dd HH:mm:ss", location.timeZone))

  Map msg = explain(wx, rate, dewPointDepression, dryingLocation)
  sendEvent(name: "status", value: msg.status)
  sendEvent(name: "reason", value: msg.reason)

  if (state.settings?.enableDebug) {
    String dpInfo = dewPointDepression != null ? " DPD=${dewPointDepression}°C" : ""
    sendEvent(name: "debug",
      value: "T=${wx.temp}(${wx.tempSrc}) RH=${wx.rh}(${wx.rhSrc})${dpInfo} W=${wx.windMs}m/s(${wx.windSrc}) Lux=${wx.lux}(${wx.luxSrc}) Rain=${wx.rainRate}(${wx.rainSrc}) Loc=${dryingLocation}")
  }

  if (newLvl >= 100) finishDone()
}

/* ===================== DISPLAY HELPERS ===================== */

private void updateConditionsDisplay(Map wx) {
  StringBuilder sb = new StringBuilder()

  // Temperature
  if (wx.temp != null) {
    sb.append("${(wx.temp as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)}°C")
  }

  // Humidity
  if (wx.rh != null) {
    if (sb.length() > 0) sb.append(", ")
    sb.append("${(wx.rh as BigDecimal).setScale(0, BigDecimal.ROUND_HALF_UP)}% RH")
  }

  // Wind description
  if (wx.windMs != null) {
    if (sb.length() > 0) sb.append(", ")
    BigDecimal w = wx.windMs as BigDecimal
    String windDesc
    if (w < 0.5) windDesc = "calm"
    else if (w < 2) windDesc = "light breeze"
    else if (w < 5) windDesc = "moderate wind"
    else if (w < 10) windDesc = "strong wind"
    else windDesc = "very windy"
    sb.append(windDesc)
  }

  // Rain indicator
  if (wx.rainRate != null && wx.rainRate > 0) {
    if (sb.length() > 0) sb.append(", ")
    sb.append("RAIN")
  }

  sendEvent(name: "conditions", value: sb.toString() ?: "—")
}

private void updateTimeElapsed() {
  long startMs = (state.startedAtMs ?: 0L) as long
  if (startMs <= 0L) {
    sendEvent(name: "timeElapsed", value: "—")
    return
  }

  long elapsedMs = now() - startMs
  Integer elapsedMins = (elapsedMs / 60000L) as Integer
  sendEvent(name: "timeElapsed", value: formatDuration(elapsedMins))
}

private void updateLearningDisplay() {
  BigDecimal calFactor = (state.calibrationFactor ?: 1.0) as BigDecimal
  Integer sessions = (state.sessionCount ?: 0) as Integer

  // Display calibration factor (e.g., "1.15x" or "0.85x")
  String calDisplay = calFactor.setScale(2, BigDecimal.ROUND_HALF_UP).toString() + "x"
  sendEvent(name: "calibrationFactor", value: calFactor.setScale(2, BigDecimal.ROUND_HALF_UP))

  // Display learning status
  String status
  if (sessions == 0) {
    status = "Not calibrated"
  } else if (sessions < 5) {
    status = "Learning (${sessions} session${sessions == 1 ? '' : 's'})"
  } else {
    status = "Calibrated (${sessions} sessions)"
  }
  sendEvent(name: "learningStatus", value: status)
}

private String formatDuration(Integer totalMinutes) {
  if (totalMinutes == null || totalMinutes < 0) return "—"
  if (totalMinutes == 0) return "0m"
  if (totalMinutes >= 9999) return "Unknown"

  Integer hours = totalMinutes / 60
  Integer mins = totalMinutes % 60

  if (hours == 0) {
    return "${mins}m"
  } else if (mins == 0) {
    return "${hours}h"
  } else {
    return "${hours}h ${mins}m"
  }
}

/* ===================== MODEL ===================== */

/**
 * Calculate evaporation power (0-1 scale)
 *
 * Uses dew point depression instead of just RH for better accuracy.
 * Dew point depression = air temp - dew point
 * Higher depression = air can absorb more moisture = faster drying
 *
 * Drying location affects how much sun and wind benefit the drying.
 */
private BigDecimal calcEvap(BigDecimal t, BigDecimal rh, BigDecimal w, BigDecimal lux, BigDecimal rainRate, BigDecimal dewPointDepression, String dryingLocation) {
  // No drying in rain
  if (rainRate != null && rainRate > 0) return 0.0

  // Get location modifiers
  BigDecimal sunFactor = (LOCATION_SUN_FACTOR[dryingLocation] ?: 1.0) as BigDecimal
  BigDecimal windFactor = (LOCATION_WIND_FACTOR[dryingLocation] ?: 1.0) as BigDecimal

  // Temperature factor: 5°C to 30°C range
  BigDecimal Tn = clamp((t - 5) / 25, 0, 1)

  // Dryness factor: use dew point depression if available, otherwise use RH
  BigDecimal Dn
  if (dewPointDepression != null) {
    // Dew point depression: 0°C (saturated) to 20°C (very dry)
    // Higher depression = better drying
    Dn = clamp(dewPointDepression / 20, 0, 1)
  } else {
    // Fallback to RH-based calculation
    Dn = clamp((85 - rh) / 55, 0, 1)
  }

  // Wind factor: exponential curve, adjusted by location
  BigDecimal Wn = clamp(1 - Math.exp(-(w as double) / 2.5), 0, 1) * windFactor

  // Sun factor: based on lux, adjusted by location
  BigDecimal Sn = clamp((lux ?: 0) / 60000, 0, 1) * sunFactor

  // Weighted combination
  // - Dryness (dew point depression) is most important: 40%
  // - Temperature: 30%
  // - Wind: 20%
  // - Sun: 10%
  BigDecimal e = 0.30 * Tn + 0.40 * Dn + 0.20 * Wn + 0.10 * Sn

  // Severe conditions penalties
  if (rh >= 92) e *= 0.25        // Near saturation
  else if (rh >= 85) e *= 0.55   // High humidity
  if (t <= 2) e *= 0.4           // Near freezing

  // Dew risk: if temp is within 2°C of dew point, drying stalls
  if (dewPointDepression != null && dewPointDepression < 2) {
    e *= 0.2  // Severe penalty - condensation risk
  }

  return clamp(e, 0, 1)
}

/* ===================== USER MESSAGES ===================== */

private Map explain(Map wx, BigDecimal rate, BigDecimal dewPointDepression, String dryingLocation) {
  if (wx.rainRate != null && wx.rainRate > 0) {
    return [status: "Stalled", reason: "Rain detected (drying paused)"]
  }

  // Dew risk warning
  if (dewPointDepression != null && dewPointDepression < 2) {
    return [status: "Stalled", reason: "Dew risk - temp near dew point"]
  }

  if (dewPointDepression != null && dewPointDepression < 5) {
    return [status: "Very slow", reason: "Air nearly saturated (low dew point depression)"]
  }

  if (wx.rh >= 90) {
    return [status: "Slow drying", reason: "Very high humidity (${wx.rh}%)"]
  }

  // Location-specific messages
  if (dryingLocation == "indoor") {
    if (wx.rh >= 70) return [status: "Slow drying", reason: "Indoor with high humidity"]
    if (rate < 0.08) return [status: "Moderate drying", reason: "Indoor drying (limited airflow)"]
    return [status: "Good drying", reason: "Indoor with good air circulation"]
  }

  if (dryingLocation == "full_shade") {
    if (wx.windMs < 0.8) return [status: "Slow drying", reason: "Shaded with low wind"]
    if (rate < 0.06) return [status: "Moderate drying", reason: "Shaded area"]
    return [status: "Good drying", reason: "Shaded but good airflow"]
  }

  if (wx.windMs < 0.8) {
    return [status: "Slow drying", reason: "Low wind / airflow"]
  }

  if ((wx.lux ?: 0) < 2000 && dryingLocation == "direct_sun") {
    return [status: "Moderate drying", reason: "Overcast / low sun"]
  }

  if (rate < 0.06) {
    return [status: "Moderate drying", reason: "Conditions okay but not ideal"]
  }

  if (dewPointDepression != null && dewPointDepression > 12) {
    return [status: "Excellent drying", reason: "Very dry air (high dew point depression)"]
  }

  return [status: "Fast drying", reason: "Good conditions for drying"]
}

/* ===================== HELPERS ===================== */

private void finishDone() {
  sendEvent(name: "level", value: 100)
  sendEvent(name: "progress", value: "100% dry")
  syncWetness()
  sendEvent(name: "etaMinutes", value: 0)
  sendEvent(name: "etaDisplay", value: "Done!")
  sendEvent(name: "estimatedDoneTime", value: "Complete")
  sendEvent(name: "switch", value: "off")
  sendEvent(name: "status", value: "Done")
  sendEvent(name: "reason", value: "Laundry estimated dry")
  updateTimeElapsed()  // Final elapsed time
}

private void syncWetness() {
  Integer dry = (device.currentValue("level") ?: 0) as Integer
  Integer wet = clampInt(100 - dry, 0, 100)
  sendEvent(name: "humidity", value: wet, unit: "%")
}

private BigDecimal clamp(BigDecimal v, BigDecimal lo, BigDecimal hi) {
  if (v == null) return lo
  return (v < lo) ? lo : ((v > hi) ? hi : v)
}

private BigDecimal clamp(Number v, Number lo, Number hi) {
  if (v == null) return lo as BigDecimal
  BigDecimal val = v as BigDecimal
  BigDecimal low = lo as BigDecimal
  BigDecimal high = hi as BigDecimal
  return (val < low) ? low : ((val > high) ? high : val)
}

private Integer clampInt(Integer v, Integer lo, Integer hi) {
  return Math.max(lo, Math.min(hi, v ?: lo))
}
