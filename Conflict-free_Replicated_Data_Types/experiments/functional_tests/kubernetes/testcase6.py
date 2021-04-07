# testcase 6: Add v1 on node 1, Add v1 on node 2 + Remove on node 1, lookup on node 1
# expected true

import requests
import time

newVertex = {"vertexName": "v1"}

url = "http://localhost:"
node1_port = "7000"
node2_port = "7001"
node3_port = "7002"
addvertex_endpoint = "/addvertex"
lookupvertex_endpoint = "/lookupvertex"
removevertex_endpoint = "/removevertex"

print("adding vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
r1 = requests.post(url + node1_port + addvertex_endpoint, json=newVertex)

print(r1.text)

if (r1.text == "true"):
    print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node1_port)
    print("waiting 2 seconds for synchronization")
    time.sleep(2)


    print("adding vertex " + newVertex['vertexName'] + " on node with port " + node2_port)
    r2 = requests.post(url + node2_port + addvertex_endpoint, json=newVertex)
    print(r1.text)

    if (r2.text == "true"):
        print("succesfully added vertex " + newVertex['vertexName'] + " to node on port " + node2_port)

        print("removing vertex " + newVertex['vertexName'] + " on node with port " + node1_port)
        r1 = requests.delete(url + node1_port + removevertex_endpoint, json=newVertex)

        print(r1.text)

        print("waiting 2 seconds for synchronization")
        time.sleep(2)

        r1 = requests.get(url + node1_port + lookupvertex_endpoint + "?vertexName=" + newVertex['vertexName'])
        r2 = requests.get(url + node2_port + lookupvertex_endpoint + "?vertexName=" + newVertex['vertexName'])
        r3 = requests.get(url + node3_port + lookupvertex_endpoint + "?vertexName=" + newVertex['vertexName'])

        print(r1.text)
        print(r2.text)
        print(r3.text)


