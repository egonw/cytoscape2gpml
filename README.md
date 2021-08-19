# Cytoscape2GPML

Takes a Cytoscape `.cys` file and converts this to `.gpml` file that
can be read by PathVisio (or uploaded to [WikiPathways](https://wikipathways.org/)).

Try the following command to see the options:

```shell
groovy convertor.groovy -h
```

This code version uses Groovy but also [Bacting](https://joss.theoj.org/papers/10.21105/joss.02558/)
for handling the SMILES in the input for the Cytoscape network this was written for.
