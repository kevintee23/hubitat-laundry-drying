/**
 * Laundry Drying Progress App v7.4
 *
 * This app provides device selection dropdowns and manages a virtual device
 * that tracks laundry drying progress.
 *
 * Features:
 * - Drying location setting (direct sun, shade, indoor)
 * - Uses dew point for more accurate drying prediction
 * - Hybrid data sources (local sensors + Open-Meteo fallback)
 * - Custom lat/long override (set in device preferences)
 */

definition(
  name: "Laundry Drying Progress",
  namespace: "kevintee",
  author: "Kevin Tee",
  description: "Track laundry drying progress using local sensors or Open-Meteo weather data",
  category: "Convenience",
  iconUrl: "",
  iconX2Url: "",
  singleInstance: false
)

preferences {
  page(name: "mainPage")
}

def mainPage() {
  dynamicPage(name: "mainPage", title: "Laundry Drying Progress Setup", install: true, uninstall: true) {

    section("<b>Name</b>") {
      label title: "Name this instance:", required: true, defaultValue: "Laundry Drying Progress"
    }

    section("<b>Update Interval</b>") {
      input "updateMinutes", "number",
        title: "Update interval (minutes)",
        description: "How often to update drying progress",
        defaultValue: 5, required: true, range: "1..60"
    }

    section("<b>Drying Location</b>") {
      input "dryingLocation", "enum",
        title: "Where is the laundry drying?",
        description: "This affects how much sunlight and wind benefit drying",
        options: [
          "direct_sun": "Direct sunlight (outdoor, full sun)",
          "partial_shade": "Partial shade (outdoor, some direct sun)",
          "full_shade": "Full shade / covered (outdoor, no direct sun)",
          "indoor": "Indoor (no sun, limited airflow)"
        ],
        defaultValue: "direct_sun", required: true
    }

    section("<b>Data Sources</b> (optional - leave blank to use Open-Meteo weather)") {
      input "tempDev", "capability.temperatureMeasurement",
        title: "Temperature source device",
        description: "Device that reports outdoor temperature",
        required: false, multiple: false

      input "humDev", "capability.relativeHumidityMeasurement",
        title: "Humidity source device",
        description: "Device that reports outdoor humidity",
        required: false, multiple: false

      input "dewPointDev", "capability.sensor",
        title: "Dew point source device (optional)",
        description: "If not set, calculated from temp/humidity",
        required: false, multiple: false

      input "luxDev", "capability.illuminanceMeasurement",
        title: "Light/Lux source device",
        description: "Illuminance sensor for sun detection",
        required: false, multiple: false

      input "windDev", "capability.sensor",
        title: "Wind speed source device",
        description: "Device with wind speed attribute",
        required: false, multiple: false

      input "rainDev", "capability.sensor",
        title: "Rain source device",
        description: "Device with rain/precipitation attribute",
        required: false, multiple: false
    }

    section("<b>Drying Speed</b>") {
      input "dryingSpeed", "enum",
        title: "Drying speed calibration",
        description: "Adjusts ETA based on fabric type/load size",
        options: [
          "Fast": "Fast (thin fabrics, small load)",
          "Normal": "Normal (mixed fabrics, average load)",
          "Slow": "Slow (thick fabrics, large load)"
        ],
        defaultValue: "Normal", required: true
    }

    section("<b>Advanced Options</b>") {
      input "showAdvanced", "bool", title: "Show advanced options", defaultValue: false, submitOnChange: true
    }

    if (showAdvanced) {
      section("<b>Dew Point Device Settings</b>") {
        input "dewPointAttr", "enum",
          title: "Dew point attribute name",
          options: ["dew_point", "dewPoint", "dewpoint"],
          defaultValue: "dew_point", required: true
      }

      section("<b>Wind Device Settings</b>") {
        input "windAttr", "enum",
          title: "Wind attribute name",
          options: ["wind_speed", "windSpeed", "wind", "wind_kmh", "windspeed", "wind_mps", "wind_ms"],
          defaultValue: "wind_speed", required: true

        input "windUnits", "enum",
          title: "Wind units",
          options: ["km/h", "m/s"],
          defaultValue: "km/h", required: true
      }

      section("<b>Rain Device Settings</b>") {
        input "rainAttr", "enum",
          title: "Rain attribute name",
          options: ["rain_rate", "rainRate", "precipitation", "precip_rate", "rain", "rainfall_daily"],
          defaultValue: "rain_rate", required: true

        input "rainUnits", "enum",
          title: "Rain units",
          options: ["mm/hr", "mm"],
          defaultValue: "mm/hr", required: true
      }

      section("<b>Fallback & Freshness</b>") {
        input "useOpenMeteoFallback", "bool",
          title: "Use Open-Meteo fallback if device values missing/stale",
          defaultValue: true

        input "maxAgeMinutes", "number",
          title: "Max device reading age (minutes)",
          description: "Readings older than this are considered stale",
          defaultValue: 20, range: "2..240"
      }

      section("<b>Debug</b>") {
        input "enableDebug", "bool", title: "Enable debug logging", defaultValue: false
      }
    }
  }
}

def installed() {
  log.info "Laundry Drying Progress App installed"
  initialize()
}

def updated() {
  log.info "Laundry Drying Progress App updated"
  unsubscribe()
  unschedule()
  initialize()
}

def uninstalled() {
  log.info "Laundry Drying Progress App uninstalled"
  deleteChildDevices()
}

def initialize() {
  createChildDevice()

  // Subscribe to source devices if configured
  if (tempDev) subscribe(tempDev, "temperature", "sourceDeviceHandler")
  if (humDev) subscribe(humDev, "humidity", "sourceDeviceHandler")
  if (dewPointDev) subscribe(dewPointDev, settings.dewPointAttr ?: "dew_point", "sourceDeviceHandler")
  if (luxDev) subscribe(luxDev, "illuminance", "sourceDeviceHandler")
  if (windDev) subscribe(windDev, settings.windAttr ?: "wind_speed", "sourceDeviceHandler")
  if (rainDev) subscribe(rainDev, settings.rainAttr ?: "rain_rate", "sourceDeviceHandler")

  // Schedule updates
  Integer mins = (settings.updateMinutes ?: 5) as Integer
  schedule("0 */${mins} * ? * *", "scheduledUpdate")
  schedule("30 */5 * ? * *", "healthCheck")

  // Push current settings to child device
  pushSettingsToChild()
}

def sourceDeviceHandler(evt) {
  if (enableDebug) log.debug "Source device event: ${evt.name} = ${evt.value}"
}

def scheduledUpdate() {
  def child = getChildDevice(getChildDni())
  if (child) {
    child.tick()
  }
}

def healthCheck() {
  def child = getChildDevice(getChildDni())
  if (child) {
    child.healthCheck()
  }
}

// Called by child device to get weather data
def getWeatherData() {
  Integer maxAge = ((settings.maxAgeMinutes ?: 20) as Integer)

  Map out = [:]

  // Temperature
  if (tempDev) {
    def t = readFreshNumeric(tempDev, "temperature", maxAge)
    if (t) { out.temp = t.v; out.tempSrc = "device" }
  }

  // Humidity
  if (humDev) {
    def h = readFreshNumeric(humDev, "humidity", maxAge)
    if (h) { out.rh = h.v; out.rhSrc = "device" }
  }

  // Dew Point (optional device)
  if (dewPointDev) {
    String dpAttr = settings.dewPointAttr ?: "dew_point"
    def dp = readFreshNumeric(dewPointDev, dpAttr, maxAge)
    if (dp) { out.dewPoint = dp.v; out.dewPointSrc = "device" }
  }

  // Wind
  if (windDev) {
    String wAttr = settings.windAttr ?: "wind_speed"
    def w = readFreshNumeric(windDev, wAttr, maxAge)
    if (w?.v != null) {
      BigDecimal wVal = w.v as BigDecimal
      out.windMs = (settings.windUnits == "m/s") ? wVal : (wVal / 3.6)
      out.windSrc = "device:${wAttr}"
    }
  }

  // Lux
  if (luxDev) {
    def lx = readFreshNumeric(luxDev, "illuminance", maxAge)
    if (lx) { out.lux = lx.v; out.luxSrc = "device" }
  }

  // Rain
  if (rainDev) {
    String rAttr = settings.rainAttr ?: "rain_rate"
    def rr = readFreshNumeric(rainDev, rAttr, maxAge)
    if (rr?.v != null) {
      BigDecimal rVal = rr.v as BigDecimal
      out.rainRate = (settings.rainUnits == "mm/hr") ? rVal : (rVal > 0 ? 0.2 : 0.0)
      out.rainSrc = "device:${rAttr}"
    }
  }

  // Check if we need Open-Meteo fallback
  boolean needsFallback = (out.temp == null || out.rh == null || out.windMs == null || out.lux == null || out.rainRate == null)
  boolean allowFallback = (settings.useOpenMeteoFallback != false)

  if (needsFallback && allowFallback) {
    Map om = fetchOpenMeteo()
    if (!om.ok) {
      return [ok: false, status: "ERROR", statusText: "Error", reason: "Open-Meteo fallback failed: ${om.reason ?: 'unknown'}"]
    }
    if (out.temp == null) { out.temp = om.temp; out.tempSrc = "open-meteo" }
    if (out.rh == null) { out.rh = om.rh; out.rhSrc = "open-meteo" }
    if (out.windMs == null) { out.windMs = om.windMs; out.windSrc = "open-meteo" }
    if (out.rainRate == null) { out.rainRate = om.rainRate; out.rainSrc = "open-meteo" }
    if (out.lux == null) { out.lux = om.lux; out.luxSrc = "open-meteo(derived)" }
    if (out.dewPoint == null && om.dewPoint != null) { out.dewPoint = om.dewPoint; out.dewPointSrc = "open-meteo" }
  }

  // Calculate dew point if not provided (using Magnus formula)
  if (out.dewPoint == null && out.temp != null && out.rh != null) {
    out.dewPoint = calcDewPoint(out.temp as BigDecimal, out.rh as BigDecimal)
    out.dewPointSrc = "calculated"
  }

  // Validate required data
  if (out.temp == null || out.rh == null || out.windMs == null) {
    return [ok: false, status: "ERROR", statusText: "Error", reason: "Missing required data (temp/RH/wind). Select devices or enable fallback."]
  }

  // Default optional values
  if (out.lux == null) out.lux = 0
  if (out.rainRate == null) out.rainRate = 0

  // Add drying location
  out.dryingLocation = settings.dryingLocation ?: "direct_sun"

  out.ok = true
  return out
}

// Calculate dew point using Magnus-Tetens formula
private BigDecimal calcDewPoint(BigDecimal tempC, BigDecimal rh) {
  if (rh <= 0) rh = 1
  if (rh > 100) rh = 100

  double a = 17.27
  double b = 237.7
  double t = tempC as double
  double h = rh as double

  double alpha = ((a * t) / (b + t)) + Math.log(h / 100.0)
  double dewPoint = (b * alpha) / (a - alpha)

  return (dewPoint as BigDecimal).setScale(1, BigDecimal.ROUND_HALF_UP)
}

private Map readFreshNumeric(dev, String attr, Integer maxAgeMinutes) {
  if (!dev || !attr) return null

  def st = dev.currentState(attr)
  if (!st || st.value == null) return null

  long ageMs = now() - (st.date?.time ?: 0L)
  long maxMs = (long) maxAgeMinutes * 60000L
  if (st.date && ageMs > maxMs) return null

  try {
    BigDecimal v = st.value as BigDecimal
    return [v: v, ageMs: ageMs]
  } catch (e) {
    return null
  }
}

private Map fetchOpenMeteo() {
  // Check if child device has custom location settings
  def child = getChildDevice(getChildDni())
  def customLoc = child?.getLocationSettings()

  def lat = customLoc?.latitude ?: location?.latitude
  def lon = customLoc?.longitude ?: location?.longitude

  if (lat == null || lon == null) return [ok: false, reason: "Location not set. Configure in device preferences or set hub location."]

  String url = "https://api.open-meteo.com/v1/forecast" +
    "?latitude=${lat}&longitude=${lon}" +
    "&current=temperature_2m,relative_humidity_2m,dew_point_2m,wind_speed_10m,precipitation,cloud_cover" +
    "&windspeed_unit=kmh&timezone=auto"

  Map result = [ok: false, reason: "Unknown error"]

  try {
    httpGet(url) { resp ->
      def c = resp?.data?.current
      if (!c) {
        result = [ok: false, reason: "No current payload"]
        return
      }

      BigDecimal temp = c.temperature_2m as BigDecimal
      BigDecimal rh = c.relative_humidity_2m as BigDecimal
      BigDecimal dewPoint = c.dew_point_2m as BigDecimal
      BigDecimal windMs = (c.wind_speed_10m as BigDecimal) / 3.6
      BigDecimal rainRate = c.precipitation as BigDecimal

      BigDecimal cloud = c.cloud_cover as BigDecimal
      BigDecimal sunRaw = 1.0 - (cloud / 100.0)
      BigDecimal sun = (sunRaw < 0.0) ? 0.0 : ((sunRaw > 1.0) ? 1.0 : sunRaw)
      BigDecimal lux = sun * 60000

      result = [ok: true, temp: temp, rh: rh, dewPoint: dewPoint, windMs: windMs, rainRate: rainRate, lux: lux]
    }
  } catch (e) {
    result = [ok: false, reason: e.message]
  }

  return result
}

private void createChildDevice() {
  def dni = getChildDni()
  def child = getChildDevice(dni)

  if (!child) {
    log.info "Creating child device: ${dni}"
    child = addChildDevice("kevintee", "Laundry Drying Progress Device", dni, [
      name: "Laundry Drying Progress",
      label: app.label ?: "Laundry Drying Progress",
      isComponent: false
    ])
  }
}

private void deleteChildDevices() {
  getChildDevices().each { child ->
    log.info "Deleting child device: ${child.deviceNetworkId}"
    deleteChildDevice(child.deviceNetworkId)
  }
}

private String getChildDni() {
  return "LaundryDrying_${app.id}"
}

private void pushSettingsToChild() {
  def child = getChildDevice(getChildDni())
  if (child) {
    child.updateSettings([
      dryingSpeed: settings.dryingSpeed ?: "Normal",
      dryingLocation: settings.dryingLocation ?: "direct_sun",
      updateMinutes: settings.updateMinutes ?: 5,
      enableDebug: settings.enableDebug ?: false
    ])
  }
}

def getAppSettings() {
  return [
    dryingSpeed: settings.dryingSpeed ?: "Normal",
    dryingLocation: settings.dryingLocation ?: "direct_sun",
    updateMinutes: settings.updateMinutes ?: 5,
    enableDebug: settings.enableDebug ?: false
  ]
}
