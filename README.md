Tigase ZeroMQ Gateway
=====================

* Date: October 5th, 2013
* Authors: Justin Karneges <justin@fanout.io>
* Mailing List: http://lists.fanout.io/listinfo.cgi/fanout-users-fanout.io
* License: MIT

Routes XMPP stanzas between Tigase and ZeroMQ sockets.

Requirements
------------

* jzmq
* tigase libs (symlink "tigase-server" to a tigase distribution)

Build
-----

Just run ant:

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
