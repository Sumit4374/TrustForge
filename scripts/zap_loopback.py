"""Official ZAP baseline hook: restrict its local management listener, without changing rules.
The packaged runner otherwise binds its no-key test management API to0.0.0.0.
"""
import zap_common

original_options=zap_common.create_start_options
def loopback_options(mode,port,extra_params):
    options=original_options(mode,port,extra_params)
    for index,value in enumerate(options[:-1]):
        if value=='-host':options[index+1]='127.0.0.1'
    return options
zap_common.create_start_options=loopback_options
