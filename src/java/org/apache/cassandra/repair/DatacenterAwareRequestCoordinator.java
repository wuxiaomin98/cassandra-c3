/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.cassandra.repair;

import org.apache.cassandra.config.DatabaseDescriptor;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class DatacenterAwareRequestCoordinator implements IRequestCoordinator<InetAddress>
{
    private Map<String, Queue<InetAddress>> requestsByDatacenter = new HashMap<>();
    private int remaining = 0;
    private final IRequestProcessor<InetAddress> processor;

    protected DatacenterAwareRequestCoordinator(IRequestProcessor<InetAddress> processor)
    {
        this.processor = processor;
    }

    public void add(InetAddress request)
    {
        String dc = DatabaseDescriptor.getEndpointSnitch().getDatacenter(request);
        Queue<InetAddress> queue = requestsByDatacenter.get(dc);
        if (queue == null)
        {
            queue = new LinkedList<>();
            requestsByDatacenter.put(dc, queue);
        }
        queue.add(request);
        remaining++;
    }

    public void start()
    {
        for (Queue<InetAddress> requests : requestsByDatacenter.values())
        {
            if (!requests.isEmpty())
              processor.process(requests.peek());
        }
    }

    // Returns how many request remains
    public int completed(InetAddress request)
    {
        String dc = DatabaseDescriptor.getEndpointSnitch().getDatacenter(request);
        Queue<InetAddress> requests = requestsByDatacenter.get(dc);
        assert requests != null;
        assert request.equals(requests.peek());
        requests.poll();
        if (!requests.isEmpty())
            processor.process(requests.peek());
        return --remaining;
    }
}
