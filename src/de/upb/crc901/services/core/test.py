import sys
from time import sleep

from bokeh.io import output_file
from sklearn import datasets, svm, metrics

sys.stderr = open("python_std.err", "w")
sys.stdin = open("python_std.in", "w")
sys.stdout = open("python_std.out", "w")
inputFile = open("test.pipe.topy","r")
outputFile = open("test.pipe.frompy","w")
f = open("/home/manuel/Downloads/Testdatei","w")

arg = ""


def build(a):
    outputFile.write("Received build prompt\n")
    for line in a.split("\t"):
        outputFile.write(line+"\n")
        outputFile.flush()
    f.write("Received build prompt\n")
    for line in a.split("\t"):
        f.write(line+"\n")
        f.flush()

def test(a):
    outputFile.write("Received test prompt\n")
    for line in a.split("\t"):
        outputFile.write(line+"\n")
        outputFile.flush()
    f.write("Received test prompt\n")
    for line in a.split("\t"):
        f.write(line+"\n")
        f.flush()

while True:
    for line in inputFile.readlines():
        line = line[:len(line)-1]
        print(line)
        if(line == "build"):
            build(arg)
            arg = ""
        elif(line == "test"):
            test(arg)
            arg = ""
        elif(line == "quit"):
            outputFile.close()
            f.write("BYE")
            raise SystemExit(0)
        else:
            arg = arg + line + "\t"
    sleep(0.5)

