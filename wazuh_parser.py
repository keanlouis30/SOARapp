# Handles parsing the incoming JSON alert from Wazuh.
import sys
import json
from logging_utils import log_message

def get_alert_data():
    log_message("Attempting to read alert from STDIN.", level="info")
    try:
        raw_data = sys.stdin.read()

        if not raw_data:
            log_message("STDIN was empty. No alert data to process.", level="warning")
            return {}

        alert_json = json.loads(raw_data)
        log_message("Successfully parsed JSON alert.", level="info")
        return alert_json

    except json.JSONDecodeError as e:
        log_message(f"Failed to decode JSON from STDIN: {str(e)}", level="error")
        return {}
    except Exception as e:
        log_message(f"An unexpected error occurred: {str(e)}", level="critical")
        return {}