#### Wazuh-OpenDaylight SOAR Module

`Project Overview`

This Python script serves as a foundational component for implementing a Security Orchestration, Automation, and Response (SOAR) solution. It integrates Wazuh, an open-source Security Information and Event Management (SIEM) and Extended Detection and Response (XDR) platform, with OpenDaylight (ODL), a Software-Defined Networking (SDN) controller.

The primary purpose of this module is to enable real-time, automated network responses to security threats detected by Wazuh, specifically focusing on ransomware activities. This contributes directly to the objectives outlined in the thesis: "Exploring Real-Time Automated Threat Detection and Response Against Ransomware Attacks using Software Defined Networking."

`What It Does`

This Python script is designed to be executed as a Wazuh Active Response script. When a security alert of a predefined severity or type is triggered by the Wazuh Manager, this script is automatically invoked.

Upon invocation, the script performs the following key actions:

Ingests Alert Data: It receives detailed security alert information from the Wazuh Manager via standard input (STDIN). This alert data is in JSON format, containing critical context about the detected threat.

Extracts Indicators: It parses the incoming alert to extract relevant indicators of compromise (IOCs), such as the source IP (srcip), destination IP (dstip), or other network-related entities associated with the suspicious activity.

Determines Network Action: Based on the type and context of the Wazuh alert (e.g., a high-severity ransomware detection), the script intelligently determines the appropriate network response (e.g., blocking a malicious IP, isolating a compromised host).

Communicates with OpenDaylight: It leverages OpenDaylight's RESTCONF API to programmatically interact with the SDN network.

Enforces Network Policy:

Blocking: For "add" commands from Wazuh, the script sends a PUT request to OpenDaylight to install a flow rule on the specified OpenFlow switch. This rule is designed to drop packets from or to the identified malicious IP address, effectively containing the threat at the network level.

Unblocking: For "delete" commands (triggered by Wazuh's active response timeout), the script sends a DELETE request to OpenDaylight to remove the previously installed flow rule, restoring network connectivity once the threat is mitigated or the quarantine period expires.

Logs Operations: All actions, including incoming commands, extracted data, and OpenDaylight API interactions (success/failure), are logged to a dedicated file for auditing and troubleshooting.

`What It's For`

This module serves as a critical bridge in an automated cybersecurity framework, transforming passive threat detection into active, network-level defense. Its primary applications and benefits include:

Real-time Ransomware Containment: By quickly identifying ransomware indicators (e.g., suspicious network connections, C2 communication) via Wazuh and automatically pushing blocking/quarantine rules to SDN switches, the module drastically reduces the lateral movement and impact of ransomware attacks.

Automated Threat Response: It automates repetitive and time-sensitive response tasks, reducing the manual workload on Security Operations Center (SOC) analysts and enabling a faster Mean Time to Respond (MTTR) to incidents.

Enhanced Network Security: Leverages the programmability of SDN to enforce dynamic security policies, making the network itself a proactive defense mechanism.

SOAR Implementation: This script is a practical demonstration of SOAR principles, showcasing how detection (Wazuh) can directly trigger automated orchestration and response (OpenDaylight via Python).

Thesis Research Component: It directly implements a core part of the proposed "SDN-based module integrated with XDR platforms to automate ransomware detection and mitigation" for the research, providing a tangible artifact for evaluation.

