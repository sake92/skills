# Sessions service

The web replicas use the SessionStore supplied by application wiring. SharedStore is a test stand-in for the persistent production store; it is not the deployed storage implementation. emit is the platform log sink, whose collector expects one JSON object per emitted record. Preserve the public constructors and methods.

Run `scala-cli test . --server=false`. The service must support rolling deployments and multiple simultaneous replicas.
