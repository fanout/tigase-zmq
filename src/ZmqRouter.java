/*
 * Copyright (C) 2013 Fanout, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package fanout.tigase;

import java.util.Queue;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import tigase.xml.Element;
import tigase.xml.SimpleParser;
import tigase.xml.SingletonFactory;
import tigase.xml.DomBuilderHandler;
import tigase.util.TigaseStringprepException;
import tigase.xmpp.JID;
import tigase.server.AbstractMessageReceiver;
import tigase.server.Packet;
import tigase.server.DisableDisco;
import org.zeromq.ZMQ;

public class ZmqRouter extends AbstractMessageReceiver implements DisableDisco
{
	private static final String NUM_THREADS_KEY = "num-threads";
	private static final String IN_SPEC_KEY = "in-spec";
	private static final String OUT_SPEC_KEY = "out-spec";

	private static final Logger log = Logger.getLogger(ZmqRouter.class.getName());
	private static final ZMQ.Context context = ZMQ.context(1);

	private static Packet stringToPacket(String s) throws
		TigaseStringprepException
	{
		SimpleParser parser = SingletonFactory.getParserInstance();
		DomBuilderHandler domHandler = new DomBuilderHandler();
		parser.parse(domHandler, s.toCharArray(), 0, s.length());
		Queue<Element> elems = domHandler.getParsedElements();
		Element e = elems.remove();
		return Packet.packetInstance(e);
	}

	private static String packetToString(Packet p)
	{
		return p.getElement().toString();
	}

	private static class ZmqReceiver implements Runnable
	{
		private ZmqRouter router;
		private ZMQ.Socket in_sock;

		public ZmqReceiver(ZmqRouter router, String in_spec)
		{
			this.router = router;

			try
			{
				in_sock = context.socket(ZMQ.PULL);
				in_sock.bind(in_spec);
			}
			catch(Exception e)
			{
				log.log(Level.FINEST, "failed to setup in_sock", e);
			}
		}

		public void run()
		{
			while(true)
			{
				byte[] data = in_sock.recv(0);

				try
				{
					String str = new String(data, "utf-8");
					Packet p = stringToPacket(str);
					router.processOutboundPacket(p);
				}
				catch(Exception e)
				{
					log.finest("error processing outbound packet: " + e.getMessage());
				}
			}
		}
	}

	private int numThreads = 1;
	private String inSpec = "";
	private String outSpec = "";
	private Object configLock = new Object();

	private Thread in_thread = null;
	private ZMQ.Socket out_sock = null;

	public ZmqRouter()
	{
	}

	@Override
	public Map<String, Object> getDefaults(Map<String, Object> params)
	{
		Map<String, Object> defs = super.getDefaults(params);

		synchronized(configLock)
		{
			defs.put(NUM_THREADS_KEY, numThreads);
			defs.put(IN_SPEC_KEY, inSpec);
			defs.put(OUT_SPEC_KEY, outSpec);
		}

		return defs;
	}

	@Override
	public void setProperties(Map<String, Object> props)
	{
		super.setProperties(props);

		synchronized(configLock)
		{
			if(props.containsKey(NUM_THREADS_KEY))
				numThreads = ((Integer)props.get(NUM_THREADS_KEY)).intValue();
			if(props.containsKey(IN_SPEC_KEY))
				inSpec = (String)props.get(IN_SPEC_KEY);
			if(props.containsKey(OUT_SPEC_KEY))
				outSpec = (String)props.get(OUT_SPEC_KEY);

			if(in_thread == null && !inSpec.isEmpty())
			{
				in_thread = new Thread(new ZmqReceiver(this, inSpec));
				in_thread.start();
			}

			if(out_sock == null && !outSpec.isEmpty())
			{
				try
				{
					out_sock = context.socket(ZMQ.PUSH);
					out_sock.bind(outSpec);
				}
				catch(Exception e)
				{
					log.log(Level.FINEST, "failed to setup out_sock", e);
				}
			}
		}
	}

	@Override
	public int processingInThreads()
	{
		synchronized(configLock)
		{
			return numThreads;
		}
	}

	@Override
	public int processingOutThreads()
	{
		synchronized(configLock)
		{
			return numThreads;
		}
	}

	@Override
	public int hashCodeForPacket(Packet packet)
	{
		JID from = packet.getStanzaFrom();
		JID to = packet.getStanzaTo();
		if(from != null && to != null)
			return from.hashCode() ^ to.hashCode();

		// should not happen
		return 1;
	}

	@Override
	public void processPacket(Packet packet)
	{
		log.finest("inbound packet: " + packet.toString());

		JID from = packet.getStanzaFrom();
		if(from == null)
		{
			log.finest("dropping packet with no from address");
			return;
		}

		JID to = packet.getStanzaTo();
		if(to == null)
		{
			log.finest("dropping packet with no to address");
			return;
		}

		synchronized(configLock)
		{
			if(out_sock == null)
			{
				log.finest("no out_sock, dropping packet");
				return;
			}

			// out_sock is only ever set once, so if we get here then we don't
			//   have to sync on it further
		}

		try
		{
			byte[] data = packetToString(packet).getBytes("utf-8");
			out_sock.send(data, 0);
		}
		catch(Exception e)
		{
			log.finest("error processing inbound packet: " + e.getMessage());
		}
	}

	public void processOutboundPacket(Packet packet)
	{
		log.finest("outbound packet: " + packet.toString());

		JID from = packet.getStanzaFrom();
		if(from == null)
		{
			log.finest("dropping packet with no from address");
			return;
		}

		JID to = packet.getStanzaTo();
		if(to == null)
		{
			log.finest("dropping packet with no to address");
			return;
		}

		addOutPacket(packet);
	}
}
