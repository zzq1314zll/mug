/*****************************************************************************
 * ------------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License");           *
 * you may not use this file except in compliance with the License.          *
 * You may obtain a copy of the License at                                   *
 *                                                                           *
 * http://www.apache.org/licenses/LICENSE-2.0                                *
 *                                                                           *
 * Unless required by applicable law or agreed to in writing, software       *
 * distributed under the License is distributed on an "AS IS" BASIS,         *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  *
 * See the License for the specific language governing permissions and       *
 * limitations under the License.                                            *
 *****************************************************************************/
package com.google.mu.util.graph;

import static com.google.mu.util.graph.Walker.nonNullList;
import static java.util.Objects.requireNonNull;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Utility to detect cycles in graphs. For example:
 *
 * <pre>{@code
 * CycleDetector.forGraph((String n) -> graph.successors(n).stream())
 *     .detectCycleFrom(entryPoint)
 *     .ifPresent(cyclic -> logger.log("cyclic path: " + cyclic.collect(joining("->"))));
 * }</pre>
 *
 * @since 4.0
 */
public final class CycleDetector<N> {
  private final Function<? super N, ? extends Stream<? extends N>> findSuccessors;

  private CycleDetector(Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
    this.findSuccessors = requireNonNull(findSuccessors);
  }

  /**
   * Returns a {@code CycleDetector} for the graph topology as observed by the
   * {@code findSuccessors} function.
   */
  public static <N> CycleDetector<N> forGraph(
      Function<? super N, ? extends Stream<? extends N>> findSuccessors) {
    return new CycleDetector<>(findSuccessors);
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>This method will hang if the given graph is infinite without cycle (the sequence of natural
   * numbers for instance).
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *         cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   */
  @SafeVarargs
  public final Optional<Stream<N>> detectCycleFrom(N... startNodes) {
    return detectCycleFrom(nonNullList(startNodes));
  }

  /**
   * Walking from {@code startNodes}, detects if the graph has any cycle.
   *
   * <p>This method will hang if the given graph is infinite with no cycles (the sequence of natural
   * numbers for instance).
   *
   * @param startNodes the entry point nodes to start walking the graph.
   * @return The stream of nodes starting from the first of {@code startNodes} that leads to a
   *         cycle, ending with nodes along a cyclic path. The last node will also be the starting
   *         point of the cycle. That is, if {@code A} and {@code B} form a cycle, the stream ends
   *         with {@code A -> B -> A}. If there is no cycle, {@link Optional#empty} is returned.
   */
  public Optional<Stream<N>> detectCycleFrom(Iterable<? extends N> startNodes) {
    AtomicReference<N> cyclic = new AtomicReference<>();
    Set<N> seen = new HashSet<>();  // Always a superset of `currentPath`.
    LinkedHashSet<N> currentPath = new LinkedHashSet<>();
    Walker<N> walker = Walker.inGraph(findSuccessors, new Predicate<N>() {
      @Override public boolean test(N node) {
        boolean newNode = seen.add(node);
        if (newNode) {
          currentPath.add(node);
        } else if (currentPath.contains(node)) {  // A cycle's found!
          cyclic.compareAndSet(null, node);
        }
        return newNode;
      }
    });
    return walker.postOrderFrom(startNodes)
        .peek(currentPath::remove)
        .filter(n -> cyclic.get() != null)
        .findFirst()
        .map(last -> Stream.concat(currentPath.stream(), Stream.of(last, cyclic.getAndSet(null))));
  }
}
