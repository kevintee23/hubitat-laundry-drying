# Hubitat Laundry Drying Progress

A smart laundry drying tracker for [Hubitat Elevation](https://hubitat.com/) that estimates when your laundry will be dry based on real-time weather conditions.

## Features

- **Real-time drying estimation** - Tracks drying progress based on temperature, humidity, wind, and sunlight
- **Multiple data sources** - Use your own weather sensors or automatic Open-Meteo weather data
- **Dew point intelligence** - Uses dew point depression for more accurate predictions than humidity alone
- **Location-aware** - Adjusts calculations based on whether laundry is in direct sun, shade, or indoors
- **Dashboard-friendly** - User-friendly attributes for Hubitat dashboards (ETA, progress, conditions)
- **Ecowitt compatible** - Includes a virtual sensor driver for Ecowitt weather station data

## What's Included

| File | Type | Description |
|------|------|-------------|
| `LaundryDryingApp.groovy` | App | Main app with device selection dropdowns |
| `LaundryDryingDevice.groovy` | Driver | Virtual device that tracks drying progress |
| `EcowittVirtualSensor.groovy` | Driver | Optional: Virtual sensor for Ecowitt weather data |

---

## Installation

### Step 1: Install the Device Driver

1. In Hubitat, go to **Drivers Code**
2. Click **+ New Driver**
3. Copy the entire contents of `LaundryDryingDevice.groovy` and paste it
4. Click **Save**

### Step 2: Install the App

1. Go to **Apps Code**
2. Click **+ New App**
3. Copy the entire contents of `LaundryDryingApp.groovy` and paste it
4. Click **Save**

### Step 3: Add the App

1. Go to **Apps** → **Add User App**
2. Select **Laundry Drying Progress**
3. Configure your settings (see Configuration below)
4. Click **Done**

The app will automatically create a virtual device that you can add to your dashboards.

---

## Configuration

### Basic Settings

| Setting | Description | Recommended |
|---------|-------------|-------------|
| **Name** | Name for this instance | "Backyard Laundry" |
| **Update interval** | How often to check conditions (minutes) | 5 |
| **Drying location** | Where the laundry is drying | See table below |
| **Drying speed** | Fabric type / load size calibration | Normal |

### Drying Location Options

| Option | Use When |
|--------|----------|
| **Direct sunlight** | Outdoor clothesline in full sun |
| **Partial shade** | Some direct sun, some shade throughout the day |
| **Full shade / covered** | Under a verandah, carport, or covered area |
| **Indoor** | Inside the house, laundry room |

### Data Sources (Optional)

Leave blank to use Open-Meteo weather data automatically. Or select your own devices:

| Source | Capability Required | Notes |
|--------|---------------------|-------|
| Temperature | `temperatureMeasurement` | Outdoor temperature sensor |
| Humidity | `relativeHumidityMeasurement` | Outdoor humidity sensor |
| Dew Point | `sensor` | Optional - calculated if not provided |
| Light/Lux | `illuminanceMeasurement` | Illuminance sensor |
| Wind Speed | `sensor` | Weather station with wind |
| Rain | `sensor` | Rain gauge or weather station |

---

## How to Use

### Starting Drying

**Option 1: From Device Page**
1. Go to **Devices** → find your Laundry Drying device
2. Click **startNow** or toggle the switch **On**

**Option 2: From Dashboard**
- Add the device to your dashboard
- Tap to turn On (starts drying)

**Option 3: From Rule Machine / Automation**
- Trigger: Motion sensor detects activity at clothesline
- Action: Turn on Laundry Drying device

### While Drying

The device will automatically:
- Update progress every X minutes (based on your setting)
- Show current weather conditions
- Display estimated time remaining
- Show estimated completion time

### Pausing Drying

**To pause** (keeps progress, stops updates):
- Toggle the switch **Off**
- Or click **pauseNow**

**To resume**:
- Toggle the switch **On** again
- Or click **startNow** (this resets the timer but keeps progress)

### When Done

The device will automatically:
- Set progress to 100%
- Turn itself off
- Show "Done" status

### Resetting for New Load

**To reset** (clears all progress for a new load):
1. Go to the device page
2. Click **Reset**

This clears:
- Progress back to 0%
- All timers and estimates
- Status back to "Ready"

---

## Dashboard Attributes

These attributes are available for your Hubitat dashboard tiles:

### User-Friendly (Recommended for Dashboards)

| Attribute | Example | Description |
|-----------|---------|-------------|
| `progress` | "65% dry" | Current drying progress |
| `etaDisplay` | "2h 15m" | Time remaining (formatted) |
| `estimatedDoneTime` | "3:45 PM" | Estimated completion time |
| `timeElapsed` | "1h 30m" | Time since drying started |
| `conditions` | "25°C, 52% RH, light breeze" | Current weather summary |
| `startedAt` | "1:15 PM" | When drying started |
| `status` | "Fast drying" | Current drying status |
| `reason` | "Good conditions" | Explanation for status |
| `dryingLocation` | "Direct sun" | Where laundry is drying |

### Technical (For Advanced Users)

| Attribute | Description |
|-----------|-------------|
| `level` | 0-100 (percentage dry) |
| `humidity` | Wetness remaining (inverse of level) |
| `etaMinutes` | Raw ETA in minutes |
| `evapPower` | Evaporation power (0-1 scale) |
| `dewPointDepression` | Temperature minus dew point |
| `lastUpdate` | Last successful data refresh |

---

## Device Preferences

On the device page, you can configure:

### Custom Location (for Open-Meteo)

| Setting | Description |
|---------|-------------|
| **Latitude** | Override hub location (e.g., -33.8688) |
| **Longitude** | Override hub location (e.g., 151.2093) |

Leave blank to use your hub's configured location.

> **Tip:** Find coordinates at [latlong.net](https://www.latlong.net/)

### Default Current State (Hubitat System Setting)

| Option | Description |
|--------|-------------|
| **off** | Device starts as "off" after hub reboot (recommended) |
| **on** | Device starts as "on" after hub reboot |
| **Last state** | Restores previous state after reboot |

---

## Using with Ecowitt Weather Stations

If you have an Ecowitt weather station and want to use its data:

### Step 1: Install the Ecowitt Virtual Sensor Driver

1. Go to **Drivers Code** → **+ New Driver**
2. Paste contents of `EcowittVirtualSensor.groovy`
3. Click **Save**

### Step 2: Create Virtual Device

1. Go to **Devices** → **Add Device** → **Virtual**
2. Name: "Ecowitt Outdoor Data" (or similar)
3. Type: **Ecowitt Virtual Sensor**
4. Click **Save**

### Step 3: Push Data from Ecowitt API

Use webCoRE, Rule Machine, or a custom app to call the Ecowitt API and update the virtual sensor:

```groovy
// Example commands to update the virtual sensor:
setTemperature(25.1)      // from outdoor.temperature.value
setHumidity(52)           // from outdoor.humidity.value
setIlluminance(2820)      // from solar_and_uvi.solar.value
setWindSpeed(5.2)         // from wind.wind_speed.value
setRainRate(0.0)          // from rainfall.rain_rate.value
setDewPoint(14.6)         // from outdoor.dew_point.value
```

### Step 4: Configure Laundry Drying App

In the Laundry Drying app settings, select your Ecowitt Virtual Sensor for:
- Temperature source
- Humidity source
- Dew point source (in Advanced Options)
- Light/Lux source
- Wind source (set attribute to `wind_speed` in Advanced Options)
- Rain source (set attribute to `rain_rate` in Advanced Options)

---

## Status Messages Explained

| Status | Meaning |
|--------|---------|
| **Ready** | Waiting to start |
| **Drying** | Currently tracking |
| **Paused** | Tracking paused |
| **Done** | Laundry estimated dry |
| **Excellent drying** | Very dry air, fast drying |
| **Fast drying** | Good conditions |
| **Good drying** | Adequate conditions |
| **Moderate drying** | Slower than ideal |
| **Slow drying** | Poor conditions |
| **Very slow** | Air nearly saturated |
| **Stalled** | Not drying (rain or dew risk) |

---

## How the Drying Model Works

The app calculates an "evaporation power" (0-1 scale) based on:

| Factor | Weight | What It Measures |
|--------|--------|------------------|
| Dew Point Depression | 40% | Air's capacity to absorb moisture |
| Temperature | 30% | Warmer = faster evaporation |
| Wind | 20% | Airflow removes moisture |
| Sunlight | 10% | Solar heating of fabric |

### Location Adjustments

| Location | Sun Factor | Wind Factor |
|----------|------------|-------------|
| Direct sun | 100% | 100% |
| Partial shade | 50% | 90% |
| Full shade | 15% | 70% |
| Indoor | 0% | 30% |

### Special Conditions

- **Rain detected**: Drying stops completely
- **Dew risk** (temp within 2°C of dew point): Severe slowdown
- **Very high humidity** (>90%): Major slowdown
- **Near freezing** (<2°C): Major slowdown

---

## Troubleshooting

### "Error: Missing weather data"

- Check that your hub location is set (Settings → Location)
- Or set custom lat/long in device preferences
- Or select local sensor devices in app settings

### ETA shows "Unknown"

- Conditions may be too poor to estimate
- Check the `reason` attribute for details

### Progress not updating

- Ensure the device switch is "on"
- Check `weatherStatus` attribute for errors
- Verify data sources are fresh (check `lastUpdate`)

### Commands not showing in webCoRE

- Make sure you saved the driver after pasting
- Refresh device list in webCoRE settings
- Check that the device is using the correct driver type

---

## Tips

1. **Start simple** - Use Open-Meteo first, then add local sensors later
2. **Calibrate** - Adjust "Drying speed" setting based on your actual results
3. **Location matters** - Be accurate about sun exposure for best estimates
4. **Dashboard tile** - Use `progress` and `etaDisplay` for clean dashboard views
5. **Automate** - Use motion sensors or buttons to auto-start when you hang laundry

---

## Version History

| Version | Changes |
|---------|---------|
| 7.3 | Added user-friendly display attributes, custom lat/long support |
| 7.2 | Added custom location (lat/long) in device preferences |
| 7.1 | Added dew point support, drying location setting |
| 7.0 | Initial app + device architecture (replaced single driver) |

---

## License

MIT License - Feel free to use, modify, and share.

## Credits

Created with assistance from Claude AI.
