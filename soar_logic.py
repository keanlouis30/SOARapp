#  Contains the core decision-making logic for what action to take based on the parsed alert.

def extract_relevant_data(alert):
    ip_to_block = None
    flow_type = None
    opendaylight_node_id = "openflow:1"

    rule_id = str(alert.get("rule", {}).get("id", "N/A"))
    src_ip = alert.get("srcip")
    dst_ip = alert.get("dstip")
    agent_ip = alert.get("agent", {}).get("ip")

    log_message(f"Extracting data for Rule ID: {rule_id}")

    match rule_id:
        case "100001":
            ip_to_block = src_ip
            flow_type = 'src'
            log_message(f"Identified inbound attacker IP: {ip_to_block}")
        case "100002":
            ip_to_block = dst_ip
            flow_type = 'dst'
            log_message(f"Identified malicious C2 IP: {ip_to_block}")
        case "100003":
            ip_to_block = agent_ip
            flow_type = 'src'
            log_message(f"Identified compromised host IP for quarantine: {ip_to_block}")
        case _:
            log_message(f"No specific logic for Rule ID {rule_id}. Falling back to agent/src IP.")
            if agent_ip:
                ip_to_block = agent_ip
                flow_type = 'src'
            elif src_ip:
                ip_to_block = src_ip
                flow_type = 'src'

    return ip_to_block, flow_type, opendaylight_node_id

