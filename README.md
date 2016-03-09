Tigase ZeroMQ Gateway
=====================

* Authors: Justin Karneges <justin@fanout.io>
* Mailing List: http://lists.fanout.io/mailman/listinfo/fanout-users
* License: MIT

Routes XMPP stanzas between Tigase and ZeroMQ sockets.

Requirements
------------

* jzmq
* tigase 5.2.0 prerelease (git rev 0658c5e or later)

Build
-----

Create a symlink "tigase-server" that points to a tigase distribution, then run ant:

    ln -s /path/to/tigase tigase-server
    ant

This will give you build/jar/tigase-zmq.jar, which should be copied into your tigase jars directory.

Configure
---------

In tigase init.properties, create a ZmqRouter instance and map any domains to it:

    --virt-hosts=example.com:comps=zrouter
    --comp-name-1=zrouter
    --comp-class-1=fanout.tigase.ZmqRouter
    zrouter/num-threads[I]=10
    zrouter/in-spec=tcp://127.0.0.1:9200
    zrouter/out-spec=tcp://127.0.0.1:9201

Sockets
-------

* in-spec: PULL bind of stanza messages
* out-spec: PUSH bind of stanza messages

Message Format
--------------

A stanza message is a single part message where the content is a full XMPP stanza (message, presence, iq) in the "jabber:client" namespace.
