/**
 *
 * Copyright (c) 2006-2016, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.codegen.lang.interfaces;

import com.speedment.codegen.lang.models.Value;
import java.util.Optional;

/**
 * A trait for models that has a {@link Value}.
 * 
 * @author Emil Forslund
 * @param <T> The extending type
 */
public interface HasValue<T extends HasValue<T>> {
    
    /**
     * Sets the value of this model.
     * 
     * @param val  the new value
     * @return     a reference to this
     */
	T set(final Value<?> val);
    
    /**
     * Returns the value of this model.
     * 
     * @return  the value
     */
	Optional<Value<?>> getValue();
}