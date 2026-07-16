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

package com.ebremer.ns;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.ResourceFactory;

public class STAT {

/**
 *  POSIX File Status
 *  <p>
 *	See <a href="http://www.w3.org/ns/posix/stat">POSIX File Status</a>.
 *  <p>
 *  <a href="http://www.w3.org/ns/posix/stat>Base URI and namepace</a>.
 */
    public static final String NS = "http://www.w3.org/ns/posix/stat#";

    public static final Property atime = ResourceFactory.createProperty(NS, "atime");
    public static final Property blksize = ResourceFactory.createProperty(NS, "blksize");
    public static final Property blocks = ResourceFactory.createProperty(NS, "blocks");
    public static final Property ctime = ResourceFactory.createProperty(NS, "ctime");
    public static final Property dev = ResourceFactory.createProperty(NS, "dev");
    public static final Property gid = ResourceFactory.createProperty(NS, "gid");
    public static final Property ino = ResourceFactory.createProperty(NS, "ino");
    public static final Property mode = ResourceFactory.createProperty(NS, "mode");
    public static final Property mtime = ResourceFactory.createProperty(NS, "mtime");
    public static final Property nlink = ResourceFactory.createProperty(NS, "nlink");
    public static final Property rdev = ResourceFactory.createProperty(NS, "rdev");
    public static final Property size = ResourceFactory.createProperty(NS, "size");
    public static final Property uid = ResourceFactory.createProperty(NS, "uid");  
}
