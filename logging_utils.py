import logging

logging.basicConfig(
    level=logging.DEBUG,  # Set the minimum level of messages to log
    format='[%(asctime)s.%(msecs)03d] %(levelname)s: %(message)s',
    datefmt='%Y-%m-%d %H:%M:%S',  # Define the timestamp format
    filename="test.log"
)

def log_message(message, level):
    """
    Log a message with the specified level.
    
    Args:
        message (str): The message to log
        level (str): The log level - "DEBUG", "INFO", "WARNING", "ERROR", "CRITICAL"
    """
    level = level.upper()
    if level == "DEBUG":
        logging.debug(message)
    elif level == "INFO":
        logging.info(message)
    elif level == "WARNING":
        logging.warning(message)
    elif level == "ERROR":
        logging.error(message)
    elif level == "CRITICAL":
        logging.critical(message)
    else:
        logging.info(message)  # Default to INFO if level is unrecognized