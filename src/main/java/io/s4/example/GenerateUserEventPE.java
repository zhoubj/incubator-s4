/*
 * Copyright (c) 2011 Yahoo! Inc. All rights reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *          http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License. See accompanying LICENSE file. 
 */
package io.s4.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import io.s4.App;
import io.s4.Event;
import io.s4.SingletonPE;
import io.s4.Stream;

public class GenerateUserEventPE extends SingletonPE {

    static String userIds[] = { "pepe", "jose", "tito", "mr_smith", "joe" };
    static int[] ages = { 25, 2, 33, 6, 67 };
    static char[] genders = { 'f', 'm' };
    final private Stream<UserEvent>[] targetStreams;
    final private Random generator = new Random();

    public GenerateUserEventPE(App app, Stream<UserEvent>... targetStreams) {
        super(app);
        this.targetStreams = targetStreams;
    }

    /* 
     * 
     */
    @Override
    protected void processInputEvent(Event event) {

    }

    @Override
    public void sendEvent() {
        List<String> favorites = new ArrayList<String>();
        favorites.add("dulce de leche");
        favorites.add("strawberry");

        int indexUserID = generator.nextInt(userIds.length);
        int indexAge = generator.nextInt(ages.length);
        int indexGender = generator.nextInt(2);

        UserEvent userEvent = new UserEvent(userIds[indexUserID],
                ages[indexAge], favorites, genders[indexGender]);

        for (int i = 0; i < targetStreams.length; i++) {
            targetStreams[i].put(userEvent);
        }
    }

    @Override
    protected void removeInstanceForKey(String id) {

        System.out.println("Removing PE instance of type "
                + this.getClass().getName() + " for key " + id);

    }

    static int pickRandom(int numElements) {
        return 0;
    }
}