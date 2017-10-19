![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# The Obligation CorDapp

This is an example of how to develop a CorDapp. It implements a simple obligation state that represents a debt from one 
party to another. 

The CorDapp allows you to issue, transfer (from old lender to new lender) and settle (with cash) obligations. It also 
comes with an API and website that allows you to do all of the aforementioned things.

Obligations are issued, transferred and settled using the confidential identities features of Corda.

## Build

```
./gradlew build
```

The output will be in `build/libs`.

## Dependencies

The Obligation CorDapp depends on the 
[Finance CorDapp](https://dl.bintray.com/r3/corda/net/corda/corda-finance/1.0.0/corda-finance-1.0.0.jar). Put both in 
the plugins directory on your Corda node.

Feel free to submit a PR.
