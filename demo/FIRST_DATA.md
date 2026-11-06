# Put your first object on the map

Create a Kafka source, run the included Python example, and watch one orange
point move on the map. Start the [evaluation demo](README.md) and sign in first.
On the computer where you started the demo, you also need Python 3.10 or newer
with `venv` support and internet access to install the example's dependency.
On Debian or Ubuntu, install `python3-venv` if venv support is missing.

## Create a Kafka source

1. Open **Data Sources**, then **New Data Source**.
2. Select **Kafka**, click **Next**, then select **Native**.
3. Enter **Kafka demo** as **Server Name**, keep the default **Read** setting,
   and click **Next**.
4. Leave **Producer byte rate** blank and click **Next**.
5. Select **GEOJSON-FEATURE**, click **Next**, then **Confirm**.
6. Click **Download All** to save `kafka-credentials.json`, then **Close** and
   **I saved them**.

Keep the downloaded file private and outside your checkout. It is the only
input the example needs; no mapper configuration or script editing is required.

## Send data and watch it move

Return to the map view **before running the producer**. Zoom toward Washington,
DC, near latitude 38.90, longitude -77.04. Keep the map visible alongside your
terminal if possible.

In a terminal on that computer, from this `demo/` directory, run:

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python produce.py "$HOME/Downloads/kafka-credentials.json"
```

Use your actual downloaded file path if it differs. The script sends **60 updates,
one second apart**, then stops automatically. While it runs:

1. Open **Layers & Sources** on the map and expand **Data Sources**.
2. **Kafka demo** should show a count of **1** after data arrives. The source
   is visible by default; enable it if it is hidden.
3. Open **Actions for Kafka demo**, then **Show object list**.
4. Click the **Kafka demo** object row to fly to the orange point. Zoom in and
   watch it move around a small circle.

If the script finishes before you find the point, rerun the last command.
It updates the same object each time. Press **Ctrl+C** to stop early.

## What the example sends

The script creates OpenCOP-ready GeoJSON for the built-in `GEOJSON-FEATURE`
mapper, including source identity, a stable object ID, point styling and
timestamps. You can inspect `produce.py` as a starting point for your own feed.
Arbitrary GeoJSON needs its own field mapping.

The example uses only the demo's bundled local Kafka broker. It does not
configure Event Hubs, TLS or a remote broker.

## If the point does not appear

A printed acknowledgment means Kafka accepted a record; the map confirms
OpenCOP ingested it. Check the selected mapper, source visibility and object
list. If needed, run `docker compose logs --tail=100 opencop-processor` from
this `demo/` directory to check the processor logs. For connection failures,
verify the demo is running and the credentials belong to this source.

The point leaves the live map ten minutes after its last update; history is
retained. Normal stop/restart keeps the source and credentials. A
[full demo reset](README.md#start-opencop) removes its data, so create a new source
and download fresh credentials afterward. Delete the credential file when you finish.
