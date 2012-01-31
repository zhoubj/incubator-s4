/**
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
package org.apache.s4.ft;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.apache.s4.processor.AbstractPE;

/**
 * Prevents event processing thread and serialization thread to overlap on the same PE instance, which would cause consistency issues during recovery.
 * 
 * How it works:
 * - we keep track of the PE being serialized and the PE being processed
 * - access to the PE is guarded by the instance of this class
 * - if the event processing thread receives an event that is handled by a PE currently being serialized, it waits until serialization is complete
 * - this is expected to happen rarely, because there are many PEs and they don't get checkpointed all the time
 * - there is a configurable timeout for blocking the event processing thread (if triggered, an error message is displayed)
 *
 */
public class CheckpointingCoordinator {

	private static final Logger logger = Logger
			.getLogger(CheckpointingCoordinator.class);

	AbstractPE processing = null;
	AbstractPE serializing = null;

	long maxSerializationLockDuration;

	Lock lock = new ReentrantLock();
	Condition processingFinished = lock.newCondition();
	Condition serializingFinished = lock.newCondition();

	public CheckpointingCoordinator(long maxSerializationLockDuration) {
		super();
		this.maxSerializationLockDuration = maxSerializationLockDuration;
	}

	public void acquireForProcessing(AbstractPE pe) {
		lock.lock();
		try {
			if (serializing == pe) {
				try {
					if (logger.isTraceEnabled()) {
						logger.trace("processing must wait for serialization to finish for PE "
								+ pe.getId() + "/" + pe.getKeyValueString());
					}
					serializingFinished.await(maxSerializationLockDuration,
							TimeUnit.MILLISECONDS);
					acquireForProcessing(pe);
				} catch (InterruptedException e) {
					logger.error("Could not acquire permit for processing after timeout of ["
							+ maxSerializationLockDuration
							+ "] milliseconds for PE["
							+ pe.getId()
							+ "/"
							+ pe.getKeyValueString()
							+ "]\nProceeding anyway, but checkpoint may contain inconsistent value");
					serializing = null;
				}
			}
			processing = pe;
		} finally {
			lock.unlock();
		}
	}

	public void releaseFromProcessing(AbstractPE pe) {
		lock.lock();
		try {
			if (processing == pe) {
				processing = null;
				processingFinished.signal();
			} else {
				logger.warn("Cannot release from processing thread a PE that is not already in processing state");
			}
		} finally {
			lock.unlock();
		}
	}

	public void acquireForSerialization(AbstractPE pe) {
		lock.lock();
		try {
			if (processing == pe) {
				try {
					if (logger.isTraceEnabled()) {
						logger.trace("serialization must wait for processing to finish for PE "
								+ pe.getId() + "/" + pe.getKeyValueString());
					}
					processingFinished.await(maxSerializationLockDuration, TimeUnit.MILLISECONDS);
					acquireForSerialization(pe);
				} catch (InterruptedException e) {
					// we still need to make sure it is now safe to serialize
					acquireForSerialization(pe);
				}
			}
			serializing = pe;
		} finally {
			lock.unlock();
		}
	}

	public void releaseFromSerialization(AbstractPE pe)
			throws InterruptedException {
		lock.lock();
		try {
			if (serializing == pe) {
				serializing = null;
				serializingFinished.signal();
			}
		} finally {
			lock.unlock();
		}
	}
}
