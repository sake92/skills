import json
import os
import sys
import tempfile
import time
import urllib.request

RED = "\033[31m"
GREEN = "\033[32m"
RESET = "\033[0m"


def main():
    project = sys.argv[1]
    output = sys.argv[2]
    api_key = sys.argv[3]

    tmp = tempfile.NamedTemporaryFile(delete=False, suffix=".partial")
    try:
        print(GREEN + "fetching project " + project + RESET)
        req = urllib.request.Request(
            "https://api.example.com/projects/" + project,
            headers={"Authorization": "Bearer " + api_key},
        )
        data = json.load(urllib.request.urlopen(req))
        for i, item in enumerate(data["items"]):
            print("processing item %d of %d" % (i + 1, len(data["items"])))
            tmp.write((json.dumps(item) + "\n").encode())
            time.sleep(0.1)
        tmp.close()
        os.rename(tmp.name, output)
        print(json.dumps({"written": len(data["items"]), "output": output}))
    except KeyboardInterrupt:
        print("bye")
    except Exception as e:
        print(RED + "error: " + str(e) + RESET)
        sys.exit()


if __name__ == "__main__":
    main()
