# The main script that Wazuh executes, orchestrating calls to the other modules.

from . import odl_api_client
from . import wazuh_parser
from . import soar_logic
from .logging_utils import log_message


def main():
    #dito yung main 
    log_message("SOAR app started.", level="info")
    pass

if __name__ == "__main__":
    main()
 