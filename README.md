![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# The Obligation CorDapp

This CorDapp comprises a demo of an IOU-like agreement that can be issued, transfered and settled confidentially. The CorDapp includes:

* An obligation state definition that records an amount of any currency payable from one party to another. The obligation state
* A contract that facilitates the verification of issuance, transfer (from one lender to another) and settlement of obligations
* Three sets of flows for issuing, transferring and settling obligations. They work with both confidential and non-confidential obligations

The CorDapp allows you to issue, transfer (from old lender to new lender) and settle (with cash) obligations. It also 
comes with an API and website that allows you to do all of the aforementioned things.

# Instructions for setting up

1. `git clone http://github.com/roger3cev/obligation-cordapp`
2. `cd obligation-cordapp`
3. `./gradlew build`
4. `cd build/nodes`
5. `./runnodes`

At this point you will have notary/network map node running as well as three other nodes and their corresponding webservers. There should be 7 console windows in total. One for the networkmap/notary and two for each of the three nodes.

# Using the CorDapp



Feel free to submit a PR.
