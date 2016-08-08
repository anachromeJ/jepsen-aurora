# jepsen-aurora

Jepsen tests for Apache Aurora

## Report

The report is available under [report/final-report.pdf](https://github.com/anachromeJ/jepsen-aurora/blob/master/report/final-report.pdf)

## Usage

This test is performed in a docker container with a typical Jepsen test environment setup. Instructions can be found at [https://github.com/aphyr/jepsen/tree/master/docker](https://github.com/aphyr/jepsen/tree/master/docker). Essentially with docker installed, the following sequence of commands runs the test:

```
docker run --privileged -t -i tjake/jepsen
git clone https://github.com/jchli/jepsen-aurora.git
cd jepsen-aurora
mkdir -p /etc/aurora
ln -s `pwd`/scripts/clusters.json /etc/aurora/clusters.json
lein test
```

## License

Copyright © 2015 Jincheng Li, Lu Yang, Jin Fang

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
