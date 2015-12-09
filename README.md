# jepsen-aurora

Jepsen tests for Apache Aurora

## Usage

```
git clone https://github.com/jchli/jepsen-aurora.git
cd jepsen-aurora
mkdir -p /etc/aurora
ln -s `pwd`/scripts/clusters.json /etc/aurora/clusters.json
lein test
```

## License

Copyright © 2015 FIXME

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
