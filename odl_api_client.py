# Handles all direct interactions with the OpenDaylight RESTCONF API.

import requests
import json
import config 
from logging_utils import log_message 


def apply_flow_rule(ip_address, flow_type, node_id="openflow:1"):
    """
    Installs a new OpenFlow static flow rule in OpenDaylight to block traffic from/to a specific IP.

    Args:
        ip_address (str): The IP address to block (e.g., "192.168.1.10").
        flow_type (str): 'src' to block source IP, 'dst' to block destination IP.
        node_id (str): OpenFlow switch ID (default: "openflow:1").

    Returns:
        bool: True if the flow was applied successfully, False otherwise.
    """
    if flow_type not in ("src", "dst"):
        log_message(f"[ODL] Invalid flow_type '{flow_type}' for IP {ip_address}", level="error")
        return False

    # Generate a unique flow ID
    sanitized_ip = ip_address.replace('.', '-')
    flow_id = f"block-{flow_type}-ip-{sanitized_ip}"
    table_id = 0
    flow_name = f"Block {flow_type} IP {ip_address}"
    priority = 65535

    # Build the match criteria
    match = {
        "ethernet-match": {
            "ethernet-type": {
                "type": 2048  # IPv4
            }
        }
    }
    if flow_type == "src":
        match["ipv4-source"] = f"{ip_address}/32"
    else:
        match["ipv4-destination"] = f"{ip_address}/32"

    # Build the flow payload
    flow_payload = {
        "flow": [{
            "id": flow_id,
            "table_id": table_id,
            "flow-name": flow_name,
            "priority": priority,
            "match": match,
            "instructions": {
                "instruction": [{
                    "order": 0,
                    "apply-actions": {
                        "action": [{
                            "order": 0,
                            "drop-action": {}
                        }]
                    }
                }]
            }
        }]
    }

    # Construct the RESTCONF URL
    url = f"{config.ODL_FLOW_BASE_URL}/node/{node_id}/table/{table_id}/flow/{flow_id}"

    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json"
    }

    try:
        response = requests.put(
            url,
            data=json.dumps(flow_payload),
            headers=headers,
            auth=(config.ODL_USERNAME, config.ODL_PASSWORD),
            timeout=10,
            verify=False  # Set to True in production with valid SSL
        )
        response.raise_for_status()
        log_message(f"[ODL] Successfully applied flow rule: {flow_id} for IP {ip_address} ({flow_type})", level="info")
        return True
    except requests.RequestException as e:
        log_message(f"[ODL] Failed to apply flow rule: {flow_id} for IP {ip_address} ({flow_type}). Error: {e}. Response: {getattr(e.response, 'text', None)}", level="error")
        return False


def remove_flow_rule(ip_address, flow_type, node_id="openflow:1"):
    """
    Removes a previously installed OpenFlow static flow rule in OpenDaylight.

    Args:
        ip_address (str): The IP address to unblock (e.g., "192.168.1.10").
        flow_type (str): 'src' or 'dst' (must match the type used in apply_flow_rule).
        node_id (str): OpenFlow switch ID (default: "openflow:1").

    Returns:
        bool: True if the flow was removed successfully, False otherwise.
    """
    if flow_type not in ("src", "dst"):
        log_message(f"[ODL] Invalid flow_type '{flow_type}' for IP {ip_address}", level="error")
        return False

    sanitized_ip = ip_address.replace('.', '-')
    flow_id = f"block-{flow_type}-ip-{sanitized_ip}"
    table_id = 0

    url = f"{config.ODL_FLOW_BASE_URL}/node/{node_id}/table/{table_id}/flow/{flow_id}"

    headers = {
        "Accept": "application/json"
    }

    try:
        response = requests.delete(
            url,
            headers=headers,
            auth=(config.ODL_USERNAME, config.ODL_PASSWORD),
            timeout=10,
            verify=False  # Set to True in production with valid SSL
        )
        response.raise_for_status()
        log_message(f"[ODL] Successfully removed flow rule: {flow_id} for IP {ip_address} ({flow_type})", level="info")
        return True
    except requests.RequestException as e:
        log_message(f"[ODL] Failed to remove flow rule: {flow_id} for IP {ip_address} ({flow_type}). Error: {e}. Response: {getattr(e.response, 'text', None)}", level="error")
        return False