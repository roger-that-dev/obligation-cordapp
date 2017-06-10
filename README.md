![Corda](https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png)

# IOU CorDapp Version 2

This repo contains an updated version of the original IOU CorDapp. The following features have been added:

* The code has been rebased to Corda M12.1
* Nodes can self issue cash
* Nodes can transfer IOUs to other nodes (this is a demonstration of a 3 Party flow)
* Node's can fully or partially settle IOUs with the self issued cash (Either GBP, USD, or CHF) - this is a demonstration of a transaction with two types of states (Cash and IOUs)
* The web UI has been updated to facilitate transferance and settlement of IOUs

# Assumptions

* The borrower cannot go into default
* The lender always signs the settlement transactions (therefore they cannot be blocked thus forcing the borrower into default)
* In a real-world app, we would have to introduce some complexities to deal with the possibility of the borrower defaulting - the act of settling the IOU cannot be contingent on collecting the lenders signature (as clearly they can sit on the transaction, refuse to sign and force the borrower into default). Instead, the borrower would unilaterally sign a settlement transaction, proving they intended to settle the IOU before the default date.

# Pre-requisites:
  
* JDK 1.8 minimum version (1.8.131)
* IntelliJ minimum version (2017.1) 
* git

# Usage

## Running the nodes:

* Ensure Oracle JDK 1.8 is installed.
* `cd` to the directory where you want to clone this repo
* `git clone http://github.com/roger3cev/iou-cordapp-v2`
* `cd iou-cordapp-v2`
* `./gradlew deployNodes`
* `cd build/nodes`
* `./runnodes`

## Using the CorDapp:

Via the web: 

Navigate to http://localhost:PORT/web/iou to use the web interface where PORT typically starts at 10007 for NodeA, double check the node terminal window or the build.gradle file for port numbers.

Via the node shell from any node which is not the **Controller**: 

1. Use `flow start SelfIssueCashFlow amount: X, currency: GBP` to issue cash.
