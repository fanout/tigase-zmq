Tigase ZeroMQ Gateway
=====================

Routes XMPP stanzas between Tigase and ZeroMQ sockets.

Requirements:

  * jzmq
  * tigase libs (symlink "tigase-server" to a tigase distribution)

Build:

    ant

Setup init.properties:

    --virt-hosts=example.com:comps=zrouter
    --comp-name-1=zrouter
    --comp-class-1=fanout.tigase.ZmqRouter
    zrouter/num-threads[I]=10
    zrouter/in-spec=tcp://127.0.0.1:9200
    zrouter/out-spec=tcp://127.0.0.1:9201

Sockets:

  * in-spec: PULL bind of stanza messages
  * out-spec: PUSH bind of stanza messages

Format:

  A stanza message is a single part message where the content is a full XMPP stanza (message, presence, iq) in the "jabber:client" namespace.
