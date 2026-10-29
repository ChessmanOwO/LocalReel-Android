import os,sys,signal
# An isolated process group lets native cancellation terminate FFmpeg children too.
os.setsid()
if os.getpgrp()!=os.getpid(): raise RuntimeError("process isolation failed")
def terminate(signum,frame): os.killpg(os.getpgrp(),signal.SIGKILL)
signal.signal(signal.SIGTERM,terminate)
from localreel.worker import main,emit
emit({"state":"ready","pid":os.getpid()})
main()
