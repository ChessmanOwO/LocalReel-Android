import runpy,sys
sys.path.insert(0,sys.argv[1])
runpy.run_module("bootstrap",run_name="__main__")
