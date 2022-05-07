/*
 * Copyright contributors to Hyperledger Besu
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.eth.messages.snap;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.AbstractSnapMessageData;
import org.hyperledger.besu.ethereum.p2p.rlpx.wire.MessageData;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPInput;
import org.hyperledger.besu.ethereum.rlp.BytesValueRLPOutput;
import org.hyperledger.besu.ethereum.rlp.RLPInput;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.immutables.value.Value;

public final class GetTrieNodes extends AbstractSnapMessageData {

  public static GetTrieNodes readFrom(final MessageData message) {
    if (message instanceof GetTrieNodes) {
      return (GetTrieNodes) message;
    }
    final int code = message.getCode();
    if (code != SnapV1.GET_TRIE_NODES) {
      throw new IllegalArgumentException(
          String.format("Message has code %d and thus is not a GetTrieNodes.", code));
    }
    return new GetTrieNodes(message.getData());
  }

  /*public static GetTrieNodes createWithRequest(
      final Hash worldStateRootHash,
      final TrieNodeDataRequest request,
      final BigInteger responseBytes) {
    return create(Optional.empty(), worldStateRootHash, request.getPaths(), responseBytes);
  }*/

  public static GetTrieNodes create(
      final Hash worldStateRootHash,
      final List<List<Bytes>> requests,
      final BigInteger responseBytes) {
    return create(Optional.empty(), worldStateRootHash, requests, responseBytes);
  }

  public static GetTrieNodes create(
      final Optional<BigInteger> requestId,
      final Hash worldStateRootHash,
      final List<List<Bytes>> paths,
      final BigInteger responseBytes) {
    final BytesValueRLPOutput tmp = new BytesValueRLPOutput();
    tmp.startList();
    requestId.ifPresent(tmp::writeBigIntegerScalar);
    tmp.writeBytes(worldStateRootHash);
    tmp.writeList(
        paths,
        (path, rlpOutput) ->
            rlpOutput.writeList(path, (b, subRlpOutput) -> subRlpOutput.writeBytes(b)));
    tmp.writeBigIntegerScalar(responseBytes);
    tmp.endList();
    return new GetTrieNodes(tmp.encoded());
  }

  public GetTrieNodes(final Bytes data) {
    super(data);
  }

  @Override
  protected Bytes wrap(final BigInteger requestId) {
    final TrieNodesPaths paths = paths(false);
    return create(
            Optional.of(requestId),
            paths.worldStateRootHash(),
            paths.paths(),
            paths.responseBytes())
        .getData();
  }

  @Override
  public int getCode() {
    return SnapV1.GET_TRIE_NODES;
  }

  public TrieNodesPaths paths(final boolean withRequestId) {
    final RLPInput input = new BytesValueRLPInput(data, false);
    input.enterList();
    if (withRequestId) input.skipNext();
    final ImmutableTrieNodesPaths.Builder paths =
        ImmutableTrieNodesPaths.builder()
            .worldStateRootHash(Hash.wrap(Bytes32.wrap(input.readBytes32())))
            .paths(input.readList(rlpInput -> rlpInput.readList(RLPInput::readBytes)))
            .responseBytes(input.readBigIntegerScalar());
    input.leaveList();
    return paths.build();
  }

  @Value.Immutable
  public interface TrieNodesPaths {

    Hash worldStateRootHash();

    List<List<Bytes>> paths();

    BigInteger responseBytes();
  }
}
