/*
 * Copyright ConsenSys AG.
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
package org.hyperledger.besu.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hyperledger.besu.cli.config.NetworkName.CLASSIC;
import static org.hyperledger.besu.cli.config.NetworkName.DEV;
import static org.hyperledger.besu.cli.config.NetworkName.GOERLI;
import static org.hyperledger.besu.cli.config.NetworkName.KOTTI;
import static org.hyperledger.besu.cli.config.NetworkName.MAINNET;
import static org.hyperledger.besu.cli.config.NetworkName.MORDOR;
import static org.hyperledger.besu.cli.config.NetworkName.RINKEBY;
import static org.hyperledger.besu.cli.config.NetworkName.ROPSTEN;
import static org.hyperledger.besu.cli.util.CommandLineUtils.DEPENDENCY_WARNING_MSG;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.ETH;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.NET;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.PERM;
import static org.hyperledger.besu.ethereum.api.jsonrpc.RpcApis.WEB3;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.GOERLI_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.GOERLI_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.MAINNET_DISCOVERY_URL;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.RINKEBY_BOOTSTRAP_NODES;
import static org.hyperledger.besu.ethereum.p2p.config.DiscoveryConfiguration.RINKEBY_DISCOVERY_URL;
import static org.hyperledger.besu.nat.kubernetes.KubernetesNatManager.DEFAULT_BESU_SERVICE_NAME_FILTER;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.hyperledger.besu.BesuInfo;
import org.hyperledger.besu.cli.config.EthNetworkConfig;
import org.hyperledger.besu.config.GenesisConfigFile;
import org.hyperledger.besu.config.experimental.ExperimentalEIPs;
import org.hyperledger.besu.controller.TargetingGasLimitCalculator;
import org.hyperledger.besu.ethereum.api.graphql.GraphQLConfiguration;
import org.hyperledger.besu.ethereum.api.handlers.TimeoutOptions;
import org.hyperledger.besu.ethereum.api.jsonrpc.JsonRpcConfiguration;
import org.hyperledger.besu.ethereum.api.jsonrpc.RpcApi;
import org.hyperledger.besu.ethereum.api.jsonrpc.websocket.WebSocketConfiguration;
import org.hyperledger.besu.ethereum.api.tls.TlsConfiguration;
import org.hyperledger.besu.ethereum.blockcreation.GasLimitCalculator;
import org.hyperledger.besu.ethereum.core.Address;
import org.hyperledger.besu.ethereum.core.Hash;
import org.hyperledger.besu.ethereum.core.MiningParameters;
import org.hyperledger.besu.ethereum.core.PrivacyParameters;
import org.hyperledger.besu.ethereum.core.Wei;
import org.hyperledger.besu.ethereum.eth.sync.SyncMode;
import org.hyperledger.besu.ethereum.eth.sync.SynchronizerConfiguration;
import org.hyperledger.besu.ethereum.p2p.peers.EnodeURL;
import org.hyperledger.besu.ethereum.permissioning.LocalPermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.PermissioningConfiguration;
import org.hyperledger.besu.ethereum.permissioning.SmartContractPermissioningConfiguration;
import org.hyperledger.besu.ethereum.worldstate.PrunerConfiguration;
import org.hyperledger.besu.metrics.StandardMetricCategory;
import org.hyperledger.besu.metrics.prometheus.MetricsConfiguration;
import org.hyperledger.besu.nat.NatMethod;
import org.hyperledger.besu.util.number.Fraction;
import org.hyperledger.besu.util.number.Percentage;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Lists;
import com.google.common.io.Resources;
import io.vertx.core.json.JsonObject;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.Level;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.toml.Toml;
import org.apache.tuweni.toml.TomlParseResult;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import picocli.CommandLine;

public class BesuCommandTest extends CommandTestAbstract {

  private static final String ENCLAVE_URI = "http://1.2.3.4:5555";
  private static final String ENCLAVE_PUBLIC_KEY = "A1aVtMxLCUHmBVHXoZzzBgPbW/wj5axDpW9X8l91SGo=";
  private static final String VALID_NODE_ID =
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0";
  private static final String PERMISSIONING_CONFIG_TOML = "/permissioning_config.toml";
  private static final JsonRpcConfiguration DEFAULT_JSON_RPC_CONFIGURATION;
  private static final GraphQLConfiguration DEFAULT_GRAPH_QL_CONFIGURATION;
  private static final WebSocketConfiguration DEFAULT_WEB_SOCKET_CONFIGURATION;
  private static final MetricsConfiguration DEFAULT_METRICS_CONFIGURATION;
  private static final int GENESIS_CONFIG_TEST_CHAINID = 3141592;
  private static final JsonObject GENESIS_VALID_JSON =
      (new JsonObject())
          .put("config", (new JsonObject()).put("chainId", GENESIS_CONFIG_TEST_CHAINID));
  private static final JsonObject GENESIS_INVALID_DATA =
      (new JsonObject()).put("config", new JsonObject());
  private static final JsonObject VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID =
      (new JsonObject())
          .put(
              "config",
              new JsonObject().put("isquorum", true).put("chainId", GENESIS_CONFIG_TEST_CHAINID));
  private static final JsonObject INVALID_GENESIS_QUORUM_INTEROP_ENABLED_MAINNET =
      (new JsonObject()).put("config", new JsonObject().put("isquorum", true));
  private static final String ENCLAVE_PUBLIC_KEY_PATH =
      BesuCommand.class.getResource("/orion_publickey.pub").getPath();

  private final String[] validENodeStrings = {
    "enode://" + VALID_NODE_ID + "@192.168.0.1:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.2:4567",
    "enode://" + VALID_NODE_ID + "@192.168.0.3:4567"
  };

  static {
    DEFAULT_JSON_RPC_CONFIGURATION = JsonRpcConfiguration.createDefault();
    DEFAULT_GRAPH_QL_CONFIGURATION = GraphQLConfiguration.createDefault();
    DEFAULT_WEB_SOCKET_CONFIGURATION = WebSocketConfiguration.createDefault();
    DEFAULT_METRICS_CONFIGURATION = MetricsConfiguration.builder().build();
  }

  @Test
  public void callingHelpSubCommandMustDisplayUsage() {
    parseCommand("--help");
    final String expectedOutputStart = String.format("Usage:%n%nbesu [OPTIONS] [COMMAND]");
    assertThat(commandOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingHelpDisplaysDefaultRpcApisCorrectly() {
    parseCommand("--help");
    assertThat(commandOutput.toString()).contains("default: [ETH, NET, WEB3]");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingVersionDisplayBesuInfoVersion() {
    parseCommand("--version");
    assertThat(commandOutput.toString()).isEqualToIgnoringWhitespace(BesuInfo.version());
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  // Testing default values
  @Test
  public void callingBesuCommandWithoutOptionsMustSyncWithDefaultValues() throws Exception {
    parseCommand();

    final ArgumentCaptor<EthNetworkConfig> ethNetworkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);
    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder)
        .ethNetworkConfig(
            new EthNetworkConfig(
                EthNetworkConfig.jsonConfig(MAINNET),
                EthNetworkConfig.MAINNET_NETWORK_ID,
                MAINNET_BOOTSTRAP_NODES,
                MAINNET_DISCOVERY_URL));
    verify(mockRunnerBuilder).p2pAdvertisedHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).p2pListenPort(eq(30303));
    verify(mockRunnerBuilder).maxPeers(eq(25));
    verify(mockRunnerBuilder).fractionRemoteConnectionsAllowed(eq(0.6f));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(DEFAULT_JSON_RPC_CONFIGURATION));
    verify(mockRunnerBuilder).graphQLConfiguration(eq(DEFAULT_GRAPH_QL_CONFIGURATION));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(DEFAULT_WEB_SOCKET_CONFIGURATION));
    verify(mockRunnerBuilder).metricsConfiguration(eq(DEFAULT_METRICS_CONFIGURATION));
    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkArg.capture());
    verify(mockRunnerBuilder).autoLogBloomCaching(eq(true));
    verify(mockRunnerBuilder).build();

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(ethNetworkArg.capture(), any());
    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());
    verify(mockControllerBuilder).dataDirectory(isNotNull());
    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).storageProvider(storageProviderArgumentCaptor.capture());
    verify(mockControllerBuilder).gasLimitCalculator(eq(GasLimitCalculator.constant()));
    verify(mockControllerBuilder).build();

    assertThat(storageProviderArgumentCaptor.getValue()).isNotNull();
    assertThat(syncConfigurationCaptor.getValue().getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.empty());
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(1000));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(Bytes.EMPTY);
    assertThat(ethNetworkArg.getValue().getNetworkId()).isEqualTo(1);
    assertThat(ethNetworkArg.getValue().getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
  }

  // Testing each option
  @Test
  public void callingWithConfigOptionButNoConfigFileShouldDisplayHelp() {
    parseCommand("--config-file");

    final String expectedOutputStart =
        "Missing required parameter for option '--config-file' (<FILE>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButNonExistingFileShouldDisplayHelp() throws IOException {
    final Path tempConfigFilePath = createTempFile("an-invalid-file-name-without-extension", "");
    parseCommand("--config-file", tempConfigFilePath.toString());

    final String expectedOutputStart =
        "Unable to read TOML configuration file " + tempConfigFilePath;
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButTomlFileNotFoundShouldDisplayHelp() {
    parseCommand("--config-file", "./an-invalid-file-name-sdsd87sjhqoi34io23.toml");

    final String expectedOutputStart = "Unable to read TOML configuration, file not found.";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidContentTomlFileShouldDisplayHelp() throws Exception {
    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final Path tempConfigFile = createTempFile("invalid_config.toml", ".");

    parseCommand("--config-file", tempConfigFile.toString());

    final String expectedOutputStart =
        "Invalid TOML configuration: org.apache.tuweni.toml.TomlParseError: Unexpected '.', expected a-z, A-Z, 0-9, ', \", a table key, "
            + "a newline, or end-of-input (line 1, column 1)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithNoBootnodesConfig() throws Exception {
    final URL configFile = this.getClass().getResource("/no_bootnodes.toml");
    final Path toml = createTempFile("toml", Resources.toString(configFile, UTF_8));

    parseCommand("--config-file", toml.toAbsolutePath().toString());

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes()).isEmpty();

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithConfigOptionButInvalidValueTomlFileShouldDisplayHelp() throws Exception {
    // We write a config file to prevent an invalid file in resource folder to raise errors in
    // code checks (CI + IDE)
    final Path tempConfigFile = createTempFile("invalid_config.toml", "tester===========.......");
    parseCommand("--config-file", tempConfigFile.toString());

    final String expectedOutputStart =
        "Invalid TOML configuration: org.apache.tuweni.toml.TomlParseError: Unexpected '=', expected ', \", ''', \"\"\", a number, "
            + "a boolean, a date/time, an array, or a table (line 1, column 8)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedOutputStart);
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void overrideDefaultValuesIfKeyIsPresentInConfigFile() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.toml");
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final File dataFolder = temp.newFolder();
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
            .replace("/opt/besu/genesis.json", escapeTomlString(genesisFile.toString()))
            .replace(
                "data-path=\"/opt/besu\"",
                "data-path=\"" + escapeTomlString(dataFolder.getPath()) + "\"");

    final Path toml = createTempFile("toml", updatedConfig.getBytes(UTF_8));

    final List<RpcApi> expectedApis = asList(ETH, WEB3);

    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();
    jsonRpcConfiguration.setEnabled(false);
    jsonRpcConfiguration.setHost("5.6.7.8");
    jsonRpcConfiguration.setPort(5678);
    jsonRpcConfiguration.setCorsAllowedDomains(Collections.emptyList());
    jsonRpcConfiguration.setRpcApis(expectedApis);

    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();
    graphQLConfiguration.setEnabled(false);
    graphQLConfiguration.setHost("6.7.8.9");
    graphQLConfiguration.setPort(6789);

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();
    webSocketConfiguration.setEnabled(false);
    webSocketConfiguration.setHost("9.10.11.12");
    webSocketConfiguration.setPort(9101);
    webSocketConfiguration.setRpcApis(expectedApis);

    final MetricsConfiguration metricsConfiguration =
        MetricsConfiguration.builder().enabled(false).host("8.6.7.5").port(309).build();

    parseCommand("--config-file", toml.toString());

    verify(mockRunnerBuilder).discovery(eq(false));
    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).p2pAdvertisedHost(eq("1.2.3.4"));
    verify(mockRunnerBuilder).p2pListenPort(eq(1234));
    verify(mockRunnerBuilder).maxPeers(eq(42));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).graphQLConfiguration(eq(graphQLConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
    verify(mockRunnerBuilder).metricsConfiguration(eq(metricsConfiguration));
    verify(mockRunnerBuilder).build();

    final List<EnodeURL> nodes =
        asList(
            EnodeURL.fromString("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"),
            EnodeURL.fromString("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"),
            EnodeURL.fromString("enode://" + VALID_NODE_ID + "@192.168.0.1:4567"));
    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes()).isEqualTo(nodes);

    final EthNetworkConfig networkConfig =
        new EthNetworkConfig.Builder(EthNetworkConfig.getNetworkConfig(MAINNET))
            .setNetworkId(BigInteger.valueOf(42))
            .setGenesisConfig(encodeJsonGenesis(GENESIS_VALID_JSON))
            .setBootNodes(nodes)
            .setDnsDiscoveryUrl(null)
            .build();
    verify(mockControllerBuilder).dataDirectory(eq(dataFolder.toPath()));
    verify(mockControllerBuilderFactory).fromEthNetworkConfig(eq(networkConfig), any());
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    assertThat(syncConfigurationCaptor.getValue().getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfigurationCaptor.getValue().getFastSyncMinimumPeerCount()).isEqualTo(13);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsSmartContractWithoutOptionMustError() {
    parseCommand("--permissions-nodes-contract-address");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .startsWith("Missing required parameter for option '--permissions-nodes-contract-address'");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsEnabledWithoutContractAddressMustError() {
    parseCommand("--permissions-nodes-contract-enabled");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .contains("No node permissioning contract address specified");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsEnabledWithInvalidContractAddressMustError() {
    parseCommand(
        "--permissions-nodes-contract-enabled",
        "--permissions-nodes-contract-address",
        "invalid-smart-contract-address");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Invalid value");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsEnabledWithTooShortContractAddressMustError() {
    parseCommand(
        "--permissions-nodes-contract-enabled", "--permissions-nodes-contract-address", "0x1234");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Invalid value");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsSmartContractMustUseOption() {

    final String smartContractAddress = "0x0000000000000000000000000000000000001234";

    parseCommand(
        "--permissions-nodes-contract-enabled",
        "--permissions-nodes-contract-address",
        smartContractAddress);
    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setNodeSmartContractAddress(
        Address.fromHexString(smartContractAddress));
    smartContractPermissioningConfiguration.setSmartContractNodeAllowlistEnabled(true);

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final PermissioningConfiguration config = permissioningConfigurationArgumentCaptor.getValue();
    assertThat(config.getSmartContractConfig().get())
        .isEqualToComparingFieldByField(smartContractPermissioningConfiguration);

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsContractVersionDefaultValue() {
    final SmartContractPermissioningConfiguration expectedConfig =
        new SmartContractPermissioningConfiguration();
    expectedConfig.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    expectedConfig.setSmartContractNodeAllowlistEnabled(true);
    expectedConfig.setNodeSmartContractInterfaceVersion(1);

    parseCommand(
        "--permissions-nodes-contract-enabled",
        "--permissions-nodes-contract-address",
        "0x0000000000000000000000000000000000001234");

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final PermissioningConfiguration config = permissioningConfigurationArgumentCaptor.getValue();
    assertThat(config.getSmartContractConfig().get())
        .isEqualToComparingFieldByField(expectedConfig);

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissionsContractVersionSetsValue() {
    final SmartContractPermissioningConfiguration expectedConfig =
        new SmartContractPermissioningConfiguration();
    expectedConfig.setNodeSmartContractAddress(
        Address.fromHexString("0x0000000000000000000000000000000000001234"));
    expectedConfig.setSmartContractNodeAllowlistEnabled(true);
    expectedConfig.setNodeSmartContractInterfaceVersion(2);

    parseCommand(
        "--permissions-nodes-contract-enabled",
        "--permissions-nodes-contract-address",
        "0x0000000000000000000000000000000000001234",
        "--permissions-nodes-contract-version",
        "2");

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final PermissioningConfiguration config = permissioningConfigurationArgumentCaptor.getValue();
    assertThat(config.getSmartContractConfig().get())
        .isEqualToComparingFieldByField(expectedConfig);

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissionsSmartContractWithoutOptionMustError() {
    parseCommand("--permissions-accounts-contract-address");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .startsWith(
            "Missing required parameter for option '--permissions-accounts-contract-address'");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissionsEnabledWithoutContractAddressMustError() {
    parseCommand("--permissions-accounts-contract-enabled");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .contains("No account permissioning contract address specified");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissionsEnabledWithInvalidContractAddressMustError() {
    parseCommand(
        "--permissions-accounts-contract-enabled",
        "--permissions-accounts-contract-address",
        "invalid-smart-contract-address");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Invalid value");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissionsEnabledWithTooShortContractAddressMustError() {
    parseCommand(
        "--permissions-accounts-contract-enabled",
        "--permissions-accounts-contract-address",
        "0x1234");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Invalid value");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissionsSmartContractMustUseOption() {
    final String smartContractAddress = "0x0000000000000000000000000000000000001234";

    parseCommand(
        "--permissions-accounts-contract-enabled",
        "--permissions-accounts-contract-address",
        smartContractAddress);
    final SmartContractPermissioningConfiguration smartContractPermissioningConfiguration =
        new SmartContractPermissioningConfiguration();
    smartContractPermissioningConfiguration.setAccountSmartContractAddress(
        Address.fromHexString(smartContractAddress));
    smartContractPermissioningConfiguration.setSmartContractAccountAllowlistEnabled(true);

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    final PermissioningConfiguration permissioningConfiguration =
        permissioningConfigurationArgumentCaptor.getValue();
    assertThat(permissioningConfiguration.getSmartContractConfig()).isPresent();

    final SmartContractPermissioningConfiguration effectiveSmartContractConfig =
        permissioningConfiguration.getSmartContractConfig().get();
    assertThat(effectiveSmartContractConfig.isSmartContractAccountAllowlistEnabled()).isTrue();
    assertThat(effectiveSmartContractConfig.getAccountSmartContractAddress())
        .isEqualTo(Address.fromHexString(smartContractAddress));

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissioningTomlPathWithoutOptionMustDisplayUsage() {
    parseCommand("--permissions-nodes-config-file");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .startsWith("Missing required parameter for option '--permissions-nodes-config-file'");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissioningTomlPathWithoutOptionMustDisplayUsage() {
    parseCommand("--permissions-accounts-config-file");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString())
        .startsWith("Missing required parameter for option '--permissions-accounts-config-file'");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissioningEnabledWithNonexistentConfigFileMustError() {
    parseCommand(
        "--permissions-nodes-config-file-enabled",
        "--permissions-nodes-config-file",
        "file-does-not-exist");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Configuration file does not exist");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissioningEnabledWithNonexistentConfigFileMustError() {
    parseCommand(
        "--permissions-accounts-config-file-enabled",
        "--permissions-accounts-config-file",
        "file-does-not-exist");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandErrorOutput.toString()).contains("Configuration file does not exist");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissioningTomlFileWithNoPermissionsEnabledMustNotError() throws IOException {

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_TOML);
    final Path permToml = createTempFile("toml", Resources.toByteArray(configFile));
    parseCommand("--permissions-nodes-config-file", permToml.toString());

    verify(mockRunnerBuilder).build();

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissioningTomlFileWithNoPermissionsEnabledMustNotError()
      throws IOException {

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_TOML);
    final Path permToml = createTempFile("toml", Resources.toByteArray(configFile));
    parseCommand("--permissions-accounts-config-file", permToml.toString());

    verify(mockRunnerBuilder).build();

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void defaultPermissionsTomlFileWithNoPermissionsEnabledMustNotError() {
    parseCommand("--p2p-enabled", "false");

    verify(mockRunnerBuilder).build();

    assertThat(commandErrorOutput.toString()).doesNotContain("no permissions enabled");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void nodePermissioningTomlPathMustUseOption() throws IOException {
    final List<EnodeURL> allowedNodes =
        Lists.newArrayList(
            EnodeURL.fromString(
                "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.168.0.9:4567"),
            EnodeURL.fromString(
                "enode://6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0@192.169.0.9:4568"));

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_TOML);
    final Path permToml = createTempFile("toml", Resources.toByteArray(configFile));

    final String allowedNodesString =
        allowedNodes.stream().map(Object::toString).collect(Collectors.joining(","));
    parseCommand(
        "--permissions-nodes-config-file-enabled",
        "--permissions-nodes-config-file",
        permToml.toString(),
        "--bootnodes",
        allowedNodesString);
    final LocalPermissioningConfiguration localPermissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();
    localPermissioningConfiguration.setNodePermissioningConfigFilePath(permToml.toString());
    localPermissioningConfiguration.setNodeAllowlist(allowedNodes);

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    final PermissioningConfiguration config = permissioningConfigurationArgumentCaptor.getValue();
    assertThat(config.getLocalConfig().get())
        .isEqualToComparingFieldByField(localPermissioningConfiguration);

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void accountPermissioningTomlPathMustUseOption() throws IOException {

    final URL configFile = this.getClass().getResource(PERMISSIONING_CONFIG_TOML);
    final Path permToml = createTempFile("toml", Resources.toByteArray(configFile));

    parseCommand(
        "--permissions-accounts-config-file-enabled",
        "--permissions-accounts-config-file",
        permToml.toString());
    final LocalPermissioningConfiguration localPermissioningConfiguration =
        LocalPermissioningConfiguration.createDefault();
    localPermissioningConfiguration.setAccountPermissioningConfigFilePath(permToml.toString());
    localPermissioningConfiguration.setAccountAllowlist(
        Collections.singletonList("0x0000000000000000000000000000000000000009"));

    verify(mockRunnerBuilder)
        .permissioningConfiguration(permissioningConfigurationArgumentCaptor.capture());
    final PermissioningConfiguration permissioningConfiguration =
        permissioningConfigurationArgumentCaptor.getValue();
    assertThat(permissioningConfiguration.getLocalConfig()).isPresent();

    final LocalPermissioningConfiguration effectiveLocalPermissioningConfig =
        permissioningConfiguration.getLocalConfig().get();
    assertThat(effectiveLocalPermissioningConfig.isAccountAllowlistEnabled()).isTrue();
    assertThat(effectiveLocalPermissioningConfig.getAccountPermissioningConfigFilePath())
        .isEqualTo(permToml.toString());

    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void tomlThatConfiguresEverythingExceptPermissioningToml() throws IOException {
    // Load a TOML that configures literally everything (except permissioning TOML config)
    final URL configFile = this.getClass().getResource("/everything_config.toml");
    final Path toml = createTempFile("toml", Resources.toByteArray(configFile));

    // Parse it.
    final CommandLine.Model.CommandSpec spec = parseCommand("--config-file", toml.toString()).spec;
    final TomlParseResult tomlResult = Toml.parse(toml);

    // Verify we configured everything
    final HashSet<CommandLine.Model.OptionSpec> options = new HashSet<>(spec.options());

    // Except for meta-options
    options.remove(spec.optionsMap().get("--config-file"));
    options.remove(spec.optionsMap().get("--help"));
    options.remove(spec.optionsMap().get("--version"));

    for (final String tomlKey : tomlResult.keySet()) {
      final CommandLine.Model.OptionSpec optionSpec = spec.optionsMap().get("--" + tomlKey);
      assertThat(optionSpec)
          .describedAs("Option '%s' should be a configurable option.", tomlKey)
          .isNotNull();
      // Verify TOML stores it by the appropriate type
      if (optionSpec.type().equals(Boolean.class)) {
        tomlResult.getBoolean(tomlKey);
      } else if (optionSpec.isMultiValue() || optionSpec.arity().max > 1) {
        tomlResult.getArray(tomlKey);
      } else if (optionSpec.type().equals(Double.class)) {
        tomlResult.getDouble(tomlKey);
      } else if (Number.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else if (Wei.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else if (Fraction.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getDouble(tomlKey);
      } else if (Percentage.class.isAssignableFrom(optionSpec.type())) {
        tomlResult.getLong(tomlKey);
      } else {
        tomlResult.getString(tomlKey);
      }
      options.remove(optionSpec);
    }
    assertThat(
            options.stream()
                .filter(optionSpec -> !optionSpec.hidden())
                .map(CommandLine.Model.OptionSpec::longestName))
        .isEmpty();
  }

  @Test
  public void noOverrideDefaultValuesIfKeyIsNotPresentInConfigFile() throws IOException {
    final String configFile = this.getClass().getResource("/partial_config.toml").getFile();

    parseCommand("--config-file", configFile);
    final JsonRpcConfiguration jsonRpcConfiguration = JsonRpcConfiguration.createDefault();

    final GraphQLConfiguration graphQLConfiguration = GraphQLConfiguration.createDefault();

    final WebSocketConfiguration webSocketConfiguration = WebSocketConfiguration.createDefault();

    final MetricsConfiguration metricsConfiguration = MetricsConfiguration.builder().build();

    verify(mockRunnerBuilder).discovery(eq(true));
    verify(mockRunnerBuilder)
        .ethNetworkConfig(
            new EthNetworkConfig(
                EthNetworkConfig.jsonConfig(MAINNET),
                EthNetworkConfig.MAINNET_NETWORK_ID,
                MAINNET_BOOTSTRAP_NODES,
                MAINNET_DISCOVERY_URL));
    verify(mockRunnerBuilder).p2pAdvertisedHost(eq("127.0.0.1"));
    verify(mockRunnerBuilder).p2pListenPort(eq(30303));
    verify(mockRunnerBuilder).maxPeers(eq(25));
    verify(mockRunnerBuilder).limitRemoteWireConnectionsEnabled(eq(true));
    verify(mockRunnerBuilder).fractionRemoteConnectionsAllowed(eq(0.6f));
    verify(mockRunnerBuilder).jsonRpcConfiguration(eq(jsonRpcConfiguration));
    verify(mockRunnerBuilder).graphQLConfiguration(eq(graphQLConfiguration));
    verify(mockRunnerBuilder).webSocketConfiguration(eq(webSocketConfiguration));
    verify(mockRunnerBuilder).metricsConfiguration(eq(metricsConfiguration));
    verify(mockRunnerBuilder).build();
    verify(mockControllerBuilder).build();
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(5);

    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void envVariableOverridesValueFromConfigFile() {
    final String configFile = this.getClass().getResource("/partial_config.toml").getFile();
    final String expectedCoinbase = "0x0000000000000000000000000000000000000004";
    setEnvironmentVariable("BESU_MINER_COINBASE", expectedCoinbase);
    parseCommand("--config-file", configFile);

    verify(mockControllerBuilder)
        .miningParameters(
            new MiningParameters(
                Address.fromHexString(expectedCoinbase),
                DefaultCommandValues.DEFAULT_MIN_TRANSACTION_GAS_PRICE,
                DefaultCommandValues.DEFAULT_EXTRA_DATA,
                false));
  }

  @Test
  public void cliOptionOverridesEnvVariableAndConfig() {
    final String configFile = this.getClass().getResource("/partial_config.toml").getFile();
    final String expectedCoinbase = "0x0000000000000000000000000000000000000006";
    setEnvironmentVariable("BESU_MINER_COINBASE", "0x0000000000000000000000000000000000000004");
    parseCommand("--config-file", configFile, "--miner-coinbase", expectedCoinbase);

    verify(mockControllerBuilder)
        .miningParameters(
            new MiningParameters(
                Address.fromHexString(expectedCoinbase),
                DefaultCommandValues.DEFAULT_MIN_TRANSACTION_GAS_PRICE,
                DefaultCommandValues.DEFAULT_EXTRA_DATA,
                false));
  }

  @Test
  public void nodekeyOptionMustBeUsed() throws Exception {
    final File file = new File("./specific/enclavePrivateKey");

    parseCommand("--node-private-key-file", file.getPath());

    verify(mockControllerBuilder).dataDirectory(isNotNull());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void dataDirOptionMustBeUsed() {
    final Path path = Paths.get(".");

    parseCommand("--data-path", path.toString());

    verify(mockControllerBuilder).dataDirectory(pathArgumentCaptor.capture());
    verify(mockControllerBuilder).nodeKey(isNotNull());
    verify(mockControllerBuilder).build();

    assertThat(pathArgumentCaptor.getValue()).isEqualByComparingTo(path.toAbsolutePath());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void genesisPathOptionMustBeUsed() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis-file", genesisFile.toString());

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_VALID_JSON));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void testGenesisPathEthOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--genesis-file", genesisFile.toString());

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEmpty();
    assertThat(config.getDnsDiscoveryUrl()).isNull();
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(3141592));
  }

  @Test
  public void testGenesisPathMainnetEthConfig() throws Exception {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "mainnet");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEqualTo(MAINNET_BOOTSTRAP_NODES);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(MAINNET_DISCOVERY_URL);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(1));
  }

  @Test
  public void testGenesisPathGoerliEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "goerli");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEqualTo(GOERLI_BOOTSTRAP_NODES);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(GOERLI_DISCOVERY_URL);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(5));
  }

  @Test
  public void testGenesisPathRinkebyEthConfig() {
    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    parseCommand("--network", "rinkeby");

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    final EthNetworkConfig config = networkArg.getValue();
    assertThat(config.getBootNodes()).isEqualTo(RINKEBY_BOOTSTRAP_NODES);
    assertThat(config.getDnsDiscoveryUrl()).isEqualTo(RINKEBY_DISCOVERY_URL);
    assertThat(config.getNetworkId()).isEqualTo(BigInteger.valueOf(4));
  }

  @Test
  public void genesisAndNetworkMustNotBeUsedTogether() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString(), "--network", "rinkeby");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .startsWith("--network option and --genesis-file option can't be used at the same time.");
  }

  @Test
  public void defaultNetworkIdAndBootnodesForCustomNetworkOptions() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_VALID_JSON));
    assertThat(networkArg.getValue().getBootNodes()).isEmpty();
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(GENESIS_CONFIG_TEST_CHAINID);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void defaultNetworkIdForInvalidGenesisMustBeMainnetNetworkId() throws Exception {
    final Path genesisFile = createFakeGenesisFile(GENESIS_INVALID_DATA);

    parseCommand("--genesis-file", genesisFile.toString());

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getGenesisConfig())
        .isEqualTo(encodeJsonGenesis(GENESIS_INVALID_DATA));

    //    assertThat(networkArg.getValue().getNetworkId())
    //        .isEqualTo(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void predefinedNetworkIdsMustBeEqualToChainIds() {
    // check the network id against the one in mainnet genesis config
    // it implies that EthNetworkConfig.mainnet().getNetworkId() returns a value equals to the chain
    // id
    // in this network genesis file.

    final GenesisConfigFile genesisConfigFile =
        GenesisConfigFile.fromConfig(EthNetworkConfig.getNetworkConfig(MAINNET).getGenesisConfig());
    assertThat(genesisConfigFile.getConfigOptions().getChainId().isPresent()).isTrue();
    assertThat(genesisConfigFile.getConfigOptions().getChainId().get())
        .isEqualTo(EthNetworkConfig.getNetworkConfig(MAINNET).getNetworkId());
  }

  @Test
  public void identityValueTrueMustBeUsed() {
    parseCommand("--identity", "test");

    verify(mockRunnerBuilder.identityString(eq(Optional.of("test")))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueTrueMustBeUsed() {
    parseCommand("--p2p-enabled", "true");

    verify(mockRunnerBuilder.p2pEnabled(eq(true))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pEnabledOptionValueFalseMustBeUsed() {
    parseCommand("--p2p-enabled", "false");

    verify(mockRunnerBuilder.p2pEnabled(eq(false))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pOptionsRequiresServiceToBeEnabled() {
    final String[] nodes = {
      "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"
    };

    parseCommand(
        "--p2p-enabled",
        "false",
        "--bootnodes",
        String.join(",", validENodeStrings),
        "--discovery-enabled",
        "false",
        "--max-peers",
        "42",
        "--remote-connections-max-percentage",
        "50",
        "--banned-node-id",
        String.join(",", nodes),
        "--banned-node-ids",
        String.join(",", nodes));

    verifyOptionsConstraintLoggerCall(
        "--p2p-enabled",
        "--discovery-enabled",
        "--bootnodes",
        "--max-peers",
        "--banned-node-ids",
        "--remote-connections-max-percentage");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void discoveryOptionValueTrueMustBeUsed() {
    parseCommand("--discovery-enabled", "true");

    verify(mockRunnerBuilder.discovery(eq(true))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void discoveryOptionValueFalseMustBeUsed() {
    parseCommand("--discovery-enabled", "false");

    verify(mockRunnerBuilder.discovery(eq(false))).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithBootnodesOptionButNoValueMustPassEmptyBootnodeList() {
    parseCommand("--bootnodes");

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithValidBootnodeMustSucceed() {
    parseCommand(
        "--bootnodes",
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithValidBootnodeButDiscoveryDisabledMustDisplayWarning() {
    parseCommand(
        "--bootnodes",
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304",
        "--discovery-enabled",
        "false");
    assertThat(commandOutput.toString()).isEmpty();
    verify(mockRunnerBuilder).build();
    verify(mockLogger, atLeast(1)).warn("Discovery disabled: bootnodes will be ignored.");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithInvalidBootnodeMustDisplayError() {
    parseCommand("--bootnodes", "invalid_enode_url");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid enode URL syntax. Enode URL should have the following format "
            + "'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBootnodeThatHasDiscoveryDisabledMustDisplayError() {
    final String validBootnode =
        "enode://d2567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30304";
    final String invalidBootnode =
        "enode://02567893371ea5a6fa6371d483891ed0d129e79a8fc74d6df95a00a6545444cd4a6960bbffe0b4e2edcf35135271de57ee559c0909236bbc2074346ef2b5b47c@127.0.0.1:30303?discport=0";
    final String bootnodesValue = validBootnode + "," + invalidBootnode;
    parseCommand("--bootnodes", bootnodesValue);
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Bootnodes must have discovery enabled. Invalid bootnodes: " + invalidBootnode + ".";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  // This test ensures non regression on https://pegasys1.atlassian.net/browse/PAN-2387
  @Test
  public void callingWithInvalidBootnodeAndEqualSignMustDisplayError() {
    parseCommand("--bootnodes=invalid_enode_url");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid enode URL syntax. Enode URL should have the following format "
            + "'enode://<node_id>@<ip>:<listening_port>[?discport=<discovery_port>]'.";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void bootnodesOptionMustBeUsed() {
    parseCommand("--bootnodes", String.join(",", validENodeStrings));

    verify(mockRunnerBuilder).ethNetworkConfig(ethNetworkConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(ethNetworkConfigArgumentCaptor.getValue().getBootNodes())
        .isEqualTo(
            Stream.of(validENodeStrings).map(EnodeURL::fromString).collect(Collectors.toList()));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void bannedNodeIdsOptionMustBeUsed() {
    final Bytes[] nodes = {
      Bytes.fromHexString(
          "6f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"),
      Bytes.fromHexString(
          "7f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0"),
      Bytes.fromHexString(
          "0x8f8a80d14311c39f35f516fa664deaaaa13e85b2f7493f37f6144d86991ec012937307647bd3b9a82abe2974e1407241d54947bbb39763a4cac9f77166ad92a0")
    };

    final String nodeIdsArg =
        Arrays.stream(nodes).map(Bytes::toShortHexString).collect(Collectors.joining(","));
    parseCommand("--banned-node-ids", nodeIdsArg);

    verify(mockRunnerBuilder).bannedNodeIds(bytesCollectionCollector.capture());
    verify(mockRunnerBuilder).build();

    assertThat(bytesCollectionCollector.getValue().toArray()).isEqualTo(nodes);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void callingWithBannedNodeidsOptionButNoValueMustDisplayError() {
    parseCommand("--banned-node-ids");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Missing required parameter for option '--banned-node-ids' at index 0 (<NODEID>)";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void callingWithBannedNodeidsOptionWithInvalidValuesMustDisplayError() {
    parseCommand("--banned-node-ids", "0x10,20,30");
    assertThat(commandOutput.toString()).isEmpty();
    final String expectedErrorOutputStart =
        "Invalid ids supplied to '--banned-node-ids'. Expected 64 bytes in 0x10";
    assertThat(commandErrorOutput.toString()).startsWith(expectedErrorOutputStart);
  }

  @Test
  public void p2pHostAndPortOptionsAreRespected() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--p2p-host", host, "--p2p-port", String.valueOf(port));

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).p2pListenPort(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);
    assertThat(intArgumentCaptor.getValue()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pInterfaceOptionIsRespected() {

    final String ip = "1.2.3.4";
    parseCommand("--p2p-interface", ip);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    verify(mockRunnerBuilder).p2pListenInterface(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(ip);
  }

  @Test
  public void p2pHostMayBeLocalhost() {

    final String host = "localhost";
    parseCommand("--p2p-host", host);

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void p2pHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--p2p-host", host);

    verify(mockRunnerBuilder).p2pAdvertisedHost(stringArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(stringArgumentCaptor.getValue()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void maxpeersOptionMustBeUsed() {

    final int maxPeers = 123;
    parseCommand("--max-peers", String.valueOf(maxPeers));

    verify(mockRunnerBuilder).maxPeers(intArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(intArgumentCaptor.getValue()).isEqualTo(maxPeers);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void remoteConnectionsPercentageOptionMustBeUsed() {

    final int remoteConnectionsPercentage = 12;
    parseCommand(
        "--remote-connections-limit-enabled",
        "--remote-connections-max-percentage",
        String.valueOf(remoteConnectionsPercentage));

    verify(mockRunnerBuilder).fractionRemoteConnectionsAllowed(floatCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(floatCaptor.getValue())
        .isEqualTo(
            Fraction.fromPercentage(Percentage.fromInt(remoteConnectionsPercentage)).getValue());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void remoteConnectionsPercentageWithInvalidFormatMustFail() {

    parseCommand(
        "--remote-connections-limit-enabled", "--remote-connections-max-percentage", "invalid");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--remote-connections-max-percentage'",
            "should be a number between 0 and 100 inclusive");
  }

  @Test
  public void remoteConnectionsPercentageWithOutOfRangeMustFail() {

    parseCommand(
        "--remote-connections-limit-enabled", "--remote-connections-max-percentage", "150");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--remote-connections-max-percentage'",
            "should be a number between 0 and 100 inclusive");
  }

  @Test
  public void enableRandomConnectionPrioritization() {
    parseCommand("--random-peer-priority-enabled");
    verify(mockRunnerBuilder).randomPeerPriority(eq(true));
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void randomConnectionPrioritizationDisabledByDefault() {
    parseCommand();
    verify(mockRunnerBuilder).randomPeerPriority(eq(false));
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void syncMode_fast() {
    parseCommand("--sync-mode", "FAST");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void syncMode_full() {
    parseCommand("--sync-mode", "FULL");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FULL);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void syncMode_full_by_default_for_dev() {
    parseCommand("--network", "dev");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FULL);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void helpShouldDisplayFastSyncOptions() {
    parseCommand("--help");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).contains("--fast-sync-min-peers");
    // whitelist is now a hidden option
    assertThat(commandOutput.toString()).doesNotContain("whitelist");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void parsesValidFastSyncMinPeersOption() {
    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "11");
    verify(mockControllerBuilder).synchronizerConfiguration(syncConfigurationCaptor.capture());

    final SynchronizerConfiguration syncConfig = syncConfigurationCaptor.getValue();
    assertThat(syncConfig.getSyncMode()).isEqualTo(SyncMode.FAST);
    assertThat(syncConfig.getFastSyncMinimumPeerCount()).isEqualTo(11);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void parsesInvalidFastSyncMinPeersOptionWrongFormatShouldFail() {

    parseCommand("--sync-mode", "FAST", "--fast-sync-min-peers", "ten");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option '--fast-sync-min-peers': 'ten' is not an int");
  }

  @Test
  public void natMethodOptionIsParsedCorrectly() {

    parseCommand("--nat-method", "NONE");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.NONE));

    parseCommand("--nat-method", "UPNP");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.UPNP));

    parseCommand("--nat-method", "AUTO");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    parseCommand("--nat-method", "DOCKER");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.DOCKER));

    parseCommand("--nat-method", "KUBERNETES");
    verify(mockRunnerBuilder).natMethod(eq(NatMethod.KUBERNETES));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void parsesInvalidNatMethodOptionsShouldFail() {

    parseCommand("--nat-method", "invalid");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--nat-method': expected one of [UPNP, DOCKER, KUBERNETES, AUTO, NONE] (case-insensitive) but was 'invalid'");
  }

  @Test
  public void ethStatsOptionIsParsedCorrectly() {
    final String url = "besu-node:secret@host:443";
    parseCommand("--Xethstats", url);
    verify(mockRunnerBuilder).ethstatsUrl(url);
  }

  @Test
  public void ethStatsContactOptionIsParsedCorrectly() {
    final String contact = "contact@mail.net";
    parseCommand("--Xethstats", "besu-node:secret@host:443", "--Xethstats-contact", contact);
    verify(mockRunnerBuilder).ethstatsContact(contact);
  }

  @Test
  public void ethStatsContactOptionCannotBeUsedWithoutEthStatsServerProvided() {
    parseCommand("--Xethstats-contact", "besu-updated");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "The `--Xethstats-contact` requires ethstats server URL to be provided. Either remove --Xethstats-contact or provide an url (via --Xethstats=nodename:secret@host:port)");
  }

  @Test
  public void dnsEnabledOptionIsParsedCorrectly() {
    final TestBesuCommand besuCommand = parseCommand("--Xdns-enabled", "true");

    assertThat(besuCommand.getEnodeDnsConfiguration().dnsEnabled()).isTrue();
    assertThat(besuCommand.getEnodeDnsConfiguration().updateEnabled()).isFalse();
  }

  @Test
  public void dnsUpdateEnabledOptionIsParsedCorrectly() {
    final TestBesuCommand besuCommand =
        parseCommand("--Xdns-enabled", "true", "--Xdns-update-enabled", "true");

    assertThat(besuCommand.getEnodeDnsConfiguration().dnsEnabled()).isTrue();
    assertThat(besuCommand.getEnodeDnsConfiguration().updateEnabled()).isTrue();
  }

  @Test
  public void dnsUpdateEnabledOptionCannotBeUsedWithoutDnsEnabled() {
    parseCommand("--Xdns-update-enabled", "true");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "The `--Xdns-update-enabled` requires dns to be enabled. Either remove --Xdns-update-enabled or specify dns is enabled (--Xdns-enabled)");
  }

  @Test
  public void helpShouldDisplayNatMethodInfo() {
    parseCommand("--help");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).contains("--nat-method");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natMethodPropertyDefaultIsAuto() {
    parseCommand();

    verify(mockRunnerBuilder).natMethod(eq(NatMethod.AUTO));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyDefaultIsBesu() {
    parseCommand();

    verify(mockRunnerBuilder).natManagerServiceName(eq(DEFAULT_BESU_SERVICE_NAME_FILTER));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natManagerPodNamePropertyIsCorrectlyUpdated() {
    final String podName = "besu-updated";
    parseCommand("--Xnat-kube-service-name", podName);

    verify(mockRunnerBuilder).natManagerServiceName(eq(podName));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatDockerMethod() {
    parseCommand("--nat-method", "DOCKER", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natManagerPodNameCannotBeUsedWithNatNoneMethod() {
    parseCommand("--nat-method", "NONE", "--Xnat-kube-service-name", "besu-updated");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "The `--Xnat-kube-service-name` parameter is only used in kubernetes mode. Either remove --Xnat-kube-service-name or select the KUBERNETES mode (via --nat--method=KUBERNETES)");
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithKubernetes() {

    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "KUBERNETES", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithDocker() {

    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "DOCKER", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledPropertyIsCorrectlyUpdatedWithUpnp() {

    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "false");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(false));
    parseCommand("--nat-method", "UPNP", "--Xnat-method-fallback-enabled", "true");
    verify(mockRunnerBuilder).natMethodFallbackEnabled(eq(true));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void natMethodFallbackEnabledCannotBeUsedWithAutoMethod() {
    parseCommand("--nat-method", "AUTO", "--Xnat-method-fallback-enabled", "false");
    Mockito.verifyZeroInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "The `--Xnat-method-fallback-enabled` parameter cannot be used in AUTO mode. Either remove --Xnat-method-fallback-enabled or select another mode (via --nat--method=XXXX)");
  }

  @Test
  public void rpcHttpEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpEnabledPropertyMustBeUsed() {
    parseCommand("--rpc-http-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void graphQLHttpEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void graphQLHttpEnabledPropertyMustBeUsed() {
    parseCommand("--graphql-http-enabled");

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyMustBeUsed() {
    parseCommand("--rpc-http-api", "ETH,NET,PERM", "--rpc-http-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Permissions are disabled. Cannot enable PERM APIs when not using Permissions.");

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(ETH, NET, PERM);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyIgnoresDuplicatesAndMustBeUsed() {
    parseCommand("--rpc-http-api", "ETH,NET,NET", "--rpc-http-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(ETH, NET);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpOptionsRequiresServiceToBeEnabled() {
    parseCommand(
        "--rpc-http-api",
        "ETH,NET",
        "--rpc-http-host",
        "0.0.0.0",
        "--rpc-http-port",
        "1234",
        "--rpc-http-cors-origins",
        "all");

    verifyOptionsConstraintLoggerCall(
        "--rpc-http-enabled",
        "--rpc-http-host",
        "--rpc-http-port",
        "--rpc-http-cors-origins",
        "--rpc-http-api");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresTlsToBeEnabled() {
    when(storageService.getByName("rocksdb-privacy"))
        .thenReturn(Optional.of(rocksDBSPrivacyStorageFactory));
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");
    final String coinbaseStr = String.format("%040x", 1);

    parseCommand(
        "--privacy-enabled",
        "--miner-enabled",
        "--miner-coinbase=" + coinbaseStr,
        "--min-gas-price",
        "0",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--privacy-tls-keystore-file",
        "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-tls-enabled", "--privacy-tls-keystore-file");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privacyTlsOptionsRequiresPrivacyToBeEnabled() {
    parseCommand("--privacy-tls-enabled", "--privacy-tls-keystore-file", "/Users/me/key");

    verifyOptionsConstraintLoggerCall("--privacy-enabled", "--privacy-tls-enabled");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void fastSyncOptionsRequiresFastSyncModeToBeSet() {
    parseCommand("--fast-sync-min-peers", "5");

    verifyOptionsConstraintLoggerCall("--sync-mode", "--fast-sync-min-peers");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcApisPropertyWithInvalidEntryMustDisplayError() {
    parseCommand("--rpc-http-api", "BOB");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    // PicoCLI uses longest option name for message when option has multiple names, so here plural.
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option '--rpc-http-apis'");
  }

  @Test
  public void rpcHttpHostAndPortOptionsMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--rpc-http-enabled", "--rpc-http-host", host, "--rpc-http-port", String.valueOf(port));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostMayBeLocalhost() {

    final String host = "localhost";
    parseCommand("--rpc-http-enabled", "--rpc-http-host", host);

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--rpc-http-enabled", "--rpc-http-host", host);

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpTlsRequiresRpcHttpEnabled() {
    parseCommand("--rpc-http-tls-enabled");

    verifyOptionsConstraintLoggerCall("--rpc-http-enabled", "--rpc-http-tls-enabled");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpTlsWithoutKeystoreReportsError() {
    parseCommand("--rpc-http-enabled", "--rpc-http-tls-enabled");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Keystore file is required when TLS is enabled for JSON-RPC HTTP endpoint");
  }

  @Test
  public void rpcHttpTlsWithoutPasswordfileReportsError() {
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        "/tmp/test.p12");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "File containing password to unlock keystore is required when TLS is enabled for JSON-RPC HTTP endpoint");
  }

  @Test
  public void rpcHttpTlsKeystoreAndPasswordMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    final String keystoreFile = "/tmp/test.p12";
    final String keystorePasswordFile = "/tmp/test.txt";
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-host",
        host,
        "--rpc-http-port",
        String.valueOf(port),
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        keystoreFile,
        "--rpc-http-tls-keystore-password-file",
        keystorePasswordFile);

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);
    final Optional<TlsConfiguration> tlsConfiguration =
        jsonRpcConfigArgumentCaptor.getValue().getTlsConfiguration();
    assertThat(tlsConfiguration.isPresent()).isTrue();
    assertThat(tlsConfiguration.get().getKeyStorePath()).isEqualTo(Path.of(keystoreFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().isEmpty()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpTlsClientAuthWithoutKnownFileReportsError() {
    final String host = "1.2.3.4";
    final int port = 1234;
    final String keystoreFile = "/tmp/test.p12";
    final String keystorePasswordFile = "/tmp/test.txt";
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-host",
        host,
        "--rpc-http-port",
        String.valueOf(port),
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        keystoreFile,
        "--rpc-http-tls-keystore-password-file",
        keystorePasswordFile,
        "--rpc-http-tls-client-auth-enabled");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Known-clients file must be specified or CA clients must be enabled when TLS client authentication is enabled for JSON-RPC HTTP endpoint");
  }

  @Test
  public void rpcHttpTlsClientAuthWithKnownClientFile() {
    final String host = "1.2.3.4";
    final int port = 1234;
    final String keystoreFile = "/tmp/test.p12";
    final String keystorePasswordFile = "/tmp/test.txt";
    final String knownClientFile = "/tmp/knownClientFile";
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-host",
        host,
        "--rpc-http-port",
        String.valueOf(port),
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        keystoreFile,
        "--rpc-http-tls-keystore-password-file",
        keystorePasswordFile,
        "--rpc-http-tls-client-auth-enabled",
        "--rpc-http-tls-known-clients-file",
        knownClientFile);

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);
    final Optional<TlsConfiguration> tlsConfiguration =
        jsonRpcConfigArgumentCaptor.getValue().getTlsConfiguration();
    assertThat(tlsConfiguration.isPresent()).isTrue();
    assertThat(tlsConfiguration.get().getKeyStorePath()).isEqualTo(Path.of(keystoreFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().isPresent()).isTrue();
    assertThat(
            tlsConfiguration.get().getClientAuthConfiguration().get().getKnownClientsFile().get())
        .isEqualTo(Path.of(knownClientFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().get().isCaClientsEnabled())
        .isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpTlsClientAuthWithCAClient() {
    final String host = "1.2.3.4";
    final int port = 1234;
    final String keystoreFile = "/tmp/test.p12";
    final String keystorePasswordFile = "/tmp/test.txt";
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-host",
        host,
        "--rpc-http-port",
        String.valueOf(port),
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        keystoreFile,
        "--rpc-http-tls-keystore-password-file",
        keystorePasswordFile,
        "--rpc-http-tls-client-auth-enabled",
        "--rpc-http-tls-ca-clients-enabled");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);
    final Optional<TlsConfiguration> tlsConfiguration =
        jsonRpcConfigArgumentCaptor.getValue().getTlsConfiguration();
    assertThat(tlsConfiguration.isPresent()).isTrue();
    assertThat(tlsConfiguration.get().getKeyStorePath()).isEqualTo(Path.of(keystoreFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().isPresent()).isTrue();
    assertThat(
            tlsConfiguration
                .get()
                .getClientAuthConfiguration()
                .get()
                .getKnownClientsFile()
                .isEmpty())
        .isTrue();
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().get().isCaClientsEnabled())
        .isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpTlsClientAuthWithCAClientAndKnownClientFile() {
    final String host = "1.2.3.4";
    final int port = 1234;
    final String keystoreFile = "/tmp/test.p12";
    final String keystorePasswordFile = "/tmp/test.txt";
    final String knownClientFile = "/tmp/knownClientFile";
    parseCommand(
        "--rpc-http-enabled",
        "--rpc-http-host",
        host,
        "--rpc-http-port",
        String.valueOf(port),
        "--rpc-http-tls-enabled",
        "--rpc-http-tls-keystore-file",
        keystoreFile,
        "--rpc-http-tls-keystore-password-file",
        keystorePasswordFile,
        "--rpc-http-tls-client-auth-enabled",
        "--rpc-http-tls-ca-clients-enabled",
        "--rpc-http-tls-known-clients-file",
        knownClientFile);

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);
    final Optional<TlsConfiguration> tlsConfiguration =
        jsonRpcConfigArgumentCaptor.getValue().getTlsConfiguration();
    assertThat(tlsConfiguration.isPresent()).isTrue();
    assertThat(tlsConfiguration.get().getKeyStorePath()).isEqualTo(Path.of(keystoreFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().isPresent()).isTrue();
    assertThat(
            tlsConfiguration.get().getClientAuthConfiguration().get().getKnownClientsFile().get())
        .isEqualTo(Path.of(knownClientFile));
    assertThat(tlsConfiguration.get().getClientAuthConfiguration().get().isCaClientsEnabled())
        .isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void graphQLHttpHostAndPortOptionsMustBeUsed() {

    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--graphql-http-enabled",
        "--graphql-http-host",
        host,
        "--graphql-http-port",
        String.valueOf(port));

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(graphQLConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void graphQLHttpHostMayBeLocalhost() {

    final String host = "localhost";
    parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void graphQLHttpHostMayBeIPv6() {

    final String host = "2600:DB8::8545";
    parseCommand("--graphql-http-enabled", "--graphql-http-host", host);

    verify(mockRunnerBuilder).graphQLConfiguration(graphQLConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(graphQLConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsTwoDomainsMustBuildListWithBothDomains() {
    final String[] origins = {"http://domain1.com", "https://domain2.com"};
    parseCommand("--rpc-http-enabled", "--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsDoubleCommaFilteredOut() {
    final String[] origins = {"http://domain1.com", "https://domain2.com"};
    parseCommand("--rpc-http-enabled", "--rpc-http-cors-origins", String.join(",,", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsWithWildcardMustBuildListWithWildcard() {
    final String[] origins = {"*"};
    parseCommand("--rpc-http-enabled", "--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains().toArray())
        .isEqualTo(origins);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsWithAllMustBuildListWithWildcard() {
    parseCommand("--rpc-http-enabled", "--rpc-http-cors-origins", "all");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains()).containsExactly("*");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--rpc-http-enabled", "--rpc-http-cors-origins", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getCorsAllowedDomains()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpCorsOriginsNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsNoneWithAnotherDomainMustFailNoneFirst() {
    final String[] origins = {"none", "http://domain1.com"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsAllWithAnotherDomainMustFail() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com,all");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsAllWithAnotherDomainMustFailAsFlags() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com", "--rpc-http-cors-origins=all");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsWildcardWithAnotherDomainMustFail() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com,*");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsWildcardWithAnotherDomainMustFailAsFlags() {
    parseCommand("--rpc-http-cors-origins=http://domain1.com", "--rpc-http-cors-origins=*");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other domains");
  }

  @Test
  public void rpcHttpCorsOriginsInvalidRegexShouldFail() {
    final String[] origins = {"**"};
    parseCommand("--rpc-http-cors-origins", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Domain values result in invalid regex pattern");
  }

  @Test
  public void rpcHttpCorsOriginsEmptyValueFails() {
    parseCommand("--rpc-http-cors-origins=");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Domain cannot be empty string or null string.");
  }

  /** test deprecated CLI option * */
  @Deprecated
  @Test
  public void rpcHttpHostWhitelistAcceptsSingleArgument() {
    parseCommand("--host-whitelist", "a");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(1);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsSingleArgument() {
    parseCommand("--host-allowlist", "a");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(1);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsMultipleArguments() {
    parseCommand("--host-allowlist", "a,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsDoubleComma() {
    parseCommand("--host-allowlist", "a,,b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Deprecated
  @Test
  public void rpcHttpHostWhitelistAllowlistAcceptsMultipleFlags() {
    parseCommand("--host-whitelist=a", "--host-allowlist=b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistAcceptsMultipleFlags() {
    parseCommand("--host-allowlist=a", "--host-allowlist=b");

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist().size()).isEqualTo(2);
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).contains("a", "b");
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist())
        .doesNotContain("*", "localhost");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistStarWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "*"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistStarWithAnotherHostnameMustFailStarFirst() {
    final String[] origins = {"*", "friend"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistAllWithAnotherHostnameMustFail() {
    final String[] origins = {"friend", "all"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Values '*' or 'all' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistWithNoneMustBuildEmptyList() {
    final String[] origins = {"none"};
    parseCommand("--host-allowlist", String.join(",", origins));

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHostsAllowlist()).isEmpty();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcHttpHostAllowlistNoneWithAnotherDomainMustFail() {
    final String[] origins = {"http://domain1.com", "none"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistNoneWithAnotherDomainMustFailNoneFirst() {
    final String[] origins = {"none", "http://domain1.com"};
    parseCommand("--host-allowlist", String.join(",", origins));

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Value 'none' can't be used with other hostnames");
  }

  @Test
  public void rpcHttpHostAllowlistEmptyValueFails() {
    parseCommand("--host-allowlist=");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains("Hostname cannot be empty string or null string.");
  }

  @Test
  public void rpcWsRpcEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsRpcEnabledPropertyMustBeUsed() {
    parseCommand("--rpc-ws-enabled");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsOptionsRequiresServiceToBeEnabled() {
    parseCommand("--rpc-ws-api", "ETH,NET", "--rpc-ws-host", "0.0.0.0", "--rpc-ws-port", "1234");

    verifyOptionsConstraintLoggerCall(
        "--rpc-ws-enabled", "--rpc-ws-host", "--rpc-ws-port", "--rpc-ws-api");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsApiPropertyMustBeUsed() {
    parseCommand("--rpc-ws-enabled", "--rpc-ws-api", "ETH, NET");

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getRpcApis())
        .containsExactlyInAnyOrder(ETH, NET);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsHostAndPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand("--rpc-ws-enabled", "--rpc-ws-host", host, "--rpc-ws-port", String.valueOf(port));

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(wsRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsHostAndMayBeLocalhost() {
    final String host = "localhost";
    parseCommand("--rpc-ws-enabled", "--rpc-ws-host", host);

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rpcWsHostAndMayBeIPv6() {
    final String host = "2600:DB8::8545";
    parseCommand("--rpc-ws-enabled", "--rpc-ws-host", host);

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsEnabledPropertyDefaultIsFalse() {
    parseCommand();

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isFalse();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsEnabledPropertyMustBeUsed() {
    parseCommand("--metrics-enabled");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushOptionsRequiresPushToBeEnabled() {
    parseCommand(
        "--metrics-push-host",
        "0.0.0.0",
        "--metrics-push-port",
        "1234",
        "--metrics-push-interval",
        "2",
        "--metrics-push-prometheus-job",
        "job-name");

    verifyOptionsConstraintLoggerCall(
        "--metrics-push-enabled",
        "--metrics-push-host",
        "--metrics-push-port",
        "--metrics-push-interval",
        "--metrics-push-prometheus-job");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsOptionsRequiresPullMetricsToBeEnabled() {
    parseCommand("--metrics-host", "0.0.0.0", "--metrics-port", "1234");

    verifyOptionsConstraintLoggerCall("--metrics-enabled", "--metrics-host", "--metrics-port");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsHostAndPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--metrics-enabled", "--metrics-host", host, "--metrics-port", String.valueOf(port));

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);
    assertThat(metricsConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsHostMayBeLocalhost() {
    final String host = "localhost";
    parseCommand("--metrics-enabled", "--metrics-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsHostMayBeIPv6() {
    final String host = "2600:DB8::8545";
    parseCommand("--metrics-enabled", "--metrics-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsCategoryPropertyMustBeUsed() {
    parseCommand("--metrics-enabled", "--metrics-category", StandardMetricCategory.JVM.toString());

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getMetricCategories())
        .containsExactly(StandardMetricCategory.JVM);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushEnabledPropertyMustBeUsed() {
    parseCommand("--metrics-push-enabled");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().isPushEnabled()).isTrue();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushHostAndPushPortOptionMustBeUsed() {
    final String host = "1.2.3.4";
    final int port = 1234;
    parseCommand(
        "--metrics-push-enabled",
        "--metrics-push-host",
        host,
        "--metrics-push-port",
        String.valueOf(port));

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(host);
    assertThat(metricsConfigArgumentCaptor.getValue().getPushPort()).isEqualTo(port);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushHostMayBeLocalhost() {
    final String host = "localhost";
    parseCommand("--metrics-push-enabled", "--metrics-push-host", host);

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushHost()).isEqualTo(host);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPushIntervalMustBeUsed() {
    parseCommand("--metrics-push-enabled", "--metrics-push-interval", "42");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPushInterval()).isEqualTo(42);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsPrometheusJobMustBeUsed() {
    parseCommand("--metrics-push-enabled", "--metrics-push-prometheus-job", "besu-command-test");

    verify(mockRunnerBuilder).metricsConfiguration(metricsConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(metricsConfigArgumentCaptor.getValue().getPrometheusJob())
        .isEqualTo("besu-command-test");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void metricsAndMetricsPushMustNotBeUsedTogether() {
    parseCommand("--metrics-enabled", "--metrics-push-enabled");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .startsWith("--metrics-enabled option and --metrics-push-enabled option can't be used");
  }

  @Test
  public void besuDoesNotStartInMiningModeIfCoinbaseNotSet() {
    parseCommand("--miner-enabled");

    Mockito.verifyZeroInteractions(mockControllerBuilder);
  }

  @Test
  public void miningIsEnabledWhenSpecified() throws Exception {
    final String coinbaseStr = String.format("%040x", 1);
    parseCommand("--miner-enabled", "--miner-coinbase=" + coinbaseStr);

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);

    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().isMiningEnabled()).isTrue();
    assertThat(miningArg.getValue().getCoinbase())
        .isEqualTo(Optional.of(Address.fromHexString(coinbaseStr)));
  }

  @Test
  public void stratumMiningIsEnabledWhenSpecified() throws Exception {
    final String coinbaseStr = String.format("%040x", 1);
    parseCommand("--miner-enabled", "--miner-coinbase=" + coinbaseStr, "--miner-stratum-enabled");

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);

    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().isMiningEnabled()).isTrue();
    assertThat(miningArg.getValue().getCoinbase())
        .isEqualTo(Optional.of(Address.fromHexString(coinbaseStr)));
    assertThat(miningArg.getValue().isStratumMiningEnabled()).isTrue();
  }

  @Test
  public void miningOptionsRequiresServiceToBeEnabled() {

    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    parseCommand(
        "--miner-coinbase",
        requestedCoinbase.toString(),
        "--min-gas-price",
        "42",
        "--miner-extra-data",
        "0x1122334455667788990011223344556677889900112233445566778899001122",
        "--miner-stratum-enabled");

    verifyOptionsConstraintLoggerCall(
        "--miner-enabled",
        "--miner-coinbase",
        "--min-gas-price",
        "--miner-extra-data",
        "--miner-stratum-enabled");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .startsWith(
            "Unable to mine with Stratum if mining is disabled. Either disable Stratum mining (remove --miner-stratum-enabled) or specify mining is enabled (--miner-enabled)");
  }

  @Test
  public void miningParametersAreCaptured() throws Exception {
    final Address requestedCoinbase = Address.fromHexString("0000011111222223333344444");
    final String extraDataString =
        "0x1122334455667788990011223344556677889900112233445566778899001122";
    parseCommand(
        "--miner-enabled",
        "--miner-coinbase=" + requestedCoinbase.toString(),
        "--min-gas-price=15",
        "--miner-extra-data=" + extraDataString);

    final ArgumentCaptor<MiningParameters> miningArg =
        ArgumentCaptor.forClass(MiningParameters.class);

    verify(mockControllerBuilder).miningParameters(miningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(miningArg.getValue().getCoinbase()).isEqualTo(Optional.of(requestedCoinbase));
    assertThat(miningArg.getValue().getMinTransactionGasPrice()).isEqualTo(Wei.of(15));
    assertThat(miningArg.getValue().getExtraData()).isEqualTo(Bytes.fromHexString(extraDataString));
  }

  @Test
  public void colorCanBeEnabledOrDisabledExplicitly() {
    Stream.of(true, false)
        .forEach(
            bool -> {
              parseCommand("--color-enabled", bool.toString());
              assertThat(BesuCommand.getColorEnabled()).contains(bool);
            });
  }

  @Ignore
  public void pruningIsEnabledIfSyncModeIsFast() {
    parseCommand("--sync-mode", "FAST");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Ignore
  public void pruningIsDisabledIfSyncModeIsFull() {
    parseCommand("--sync-mode", "FULL");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void pruningEnabledExplicitly() {
    parseCommand("--pruning-enabled", "--sync-mode=FULL");

    verify(mockControllerBuilder).isPruningEnabled(true);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Ignore
  public void pruningDisabledExplicitly() {
    parseCommand("--pruning-enabled=false", "--sync-mode=FAST");

    verify(mockControllerBuilder).isPruningEnabled(false);
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void pruningDisabledByDefault() {
    parseCommand();

    verify(mockControllerBuilder).isPruningEnabled(false);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void pruningParametersAreCaptured() throws Exception {
    parseCommand(
        "--pruning-enabled", "--pruning-blocks-retained=15", "--pruning-block-confirmations=4");

    final ArgumentCaptor<PrunerConfiguration> pruningArg =
        ArgumentCaptor.forClass(PrunerConfiguration.class);

    verify(mockControllerBuilder).pruningConfiguration(pruningArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(pruningArg.getValue().getBlocksRetained()).isEqualTo(15);
    assertThat(pruningArg.getValue().getBlockConfirmations()).isEqualTo(4);
  }

  @Test
  public void devModeOptionMustBeUsed() throws Exception {
    parseCommand("--network", "dev");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(DEV));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rinkebyValuesAreUsed() throws Exception {
    parseCommand("--network", "rinkeby");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(RINKEBY));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void ropstenValuesAreUsed() throws Exception {
    parseCommand("--network", "ropsten");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(ROPSTEN));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void goerliValuesAreUsed() throws Exception {
    parseCommand("--network", "goerli");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(GOERLI));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void classicValuesAreUsed() throws Exception {
    parseCommand("--network", "classic");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(CLASSIC));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void kottiValuesAreUsed() throws Exception {
    parseCommand("--network", "kotti");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(KOTTI));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void mordorValuesAreUsed() throws Exception {
    parseCommand("--network", "mordor");

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue()).isEqualTo(EthNetworkConfig.getNetworkConfig(MORDOR));

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void rinkebyValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("rinkeby");
  }

  @Test
  public void goerliValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("goerli");
  }

  @Test
  public void ropstenValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("ropsten");
  }

  @Test
  public void devValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("dev");
  }

  @Test
  public void classicValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("classic");
  }

  @Test
  public void kottiValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("kotti");
  }

  @Test
  public void mordorValuesCanBeOverridden() throws Exception {
    networkValuesCanBeOverridden("mordor");
  }

  private void networkValuesCanBeOverridden(final String network) throws Exception {
    parseCommand(
        "--network",
        network,
        "--network-id",
        "1234567",
        "--bootnodes",
        String.join(",", validENodeStrings));

    final ArgumentCaptor<EthNetworkConfig> networkArg =
        ArgumentCaptor.forClass(EthNetworkConfig.class);

    verify(mockControllerBuilderFactory).fromEthNetworkConfig(networkArg.capture(), any());
    verify(mockControllerBuilder).build();

    assertThat(networkArg.getValue().getBootNodes())
        .isEqualTo(
            Stream.of(validENodeStrings).map(EnodeURL::fromString).collect(Collectors.toList()));
    assertThat(networkArg.getValue().getNetworkId()).isEqualTo(1234567);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void fullCLIOptionsShown() {
    parseCommand("--help");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).contains("--config-file");
    assertThat(commandOutput.toString()).contains("--data-path");
    assertThat(commandOutput.toString()).contains("--genesis-file");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void mustUseEnclaveUriAndOptions() {
    final URL configFile = this.getClass().getResource("/orion_publickey.pub");

    parseCommand(
        "--privacy-enabled",
        "--privacy-url",
        ENCLAVE_URI,
        "--privacy-public-key-file",
        configFile.getPath(),
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(true);
    assertThat(enclaveArg.getValue().getEnclaveUri()).isEqualTo(URI.create(ENCLAVE_URI));
    assertThat(enclaveArg.getValue().getEnclavePublicKey()).isEqualTo(ENCLAVE_PUBLIC_KEY);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privacyOptionsRequiresServiceToBeEnabled() {

    final File file = new File("./specific/enclavePublicKey");
    file.deleteOnExit();

    parseCommand("--privacy-url", ENCLAVE_URI, "--privacy-public-key-file", file.toString());

    verifyOptionsConstraintLoggerCall(
        "--privacy-enabled", "--privacy-url", "--privacy-public-key-file");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privacyWithoutPrivacyPublicKeyFails() {
    parseCommand("--privacy-enabled", "--privacy-url", ENCLAVE_URI);

    assertThat(commandErrorOutput.toString())
        .startsWith("Please specify Enclave public key file path to enable privacy");
  }

  @Test
  public void mustVerifyPrivacyIsDisabled() {
    parseCommand();

    final ArgumentCaptor<PrivacyParameters> enclaveArg =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(enclaveArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(enclaveArg.getValue().isEnabled()).isEqualTo(false);
  }

  @Test
  public void privacyMultiTenancyIsConfiguredWhenConfiguredWithNecessaryOptions() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(privacyParametersArgumentCaptor.getValue().isMultiTenancyEnabled()).isTrue();
  }

  @Test
  public void privacyMultiTenancyWithoutAuthenticationFails() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file");

    assertThat(commandErrorOutput.toString())
        .startsWith(
            "Privacy multi-tenancy requires either http authentication to be enabled or WebSocket authentication to be enabled");
  }

  @Test
  public void privacyMultiTenancyWithPrivacyPublicKeyFileFails() {
    parseCommand(
        "--privacy-enabled",
        "--rpc-http-authentication-enabled",
        "--privacy-multi-tenancy-enabled",
        "--rpc-http-authentication-jwt-public-key-file",
        "/non/existent/file",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH);

    assertThat(commandErrorOutput.toString())
        .startsWith("Privacy multi-tenancy and privacy public key cannot be used together");
  }

  @Test
  public void onChainPrivacyGroupEnabledFlagDefaultValueIsFalse() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isOnchainPrivacyGroupsEnabled()).isEqualTo(false);
  }

  @Test
  public void onChainPrivacyGroupEnabledFlagValueIsSet() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-onchain-groups-enabled",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isOnchainPrivacyGroupsEnabled()).isEqualTo(true);
  }

  @Test
  public void flexiblePrivacyGroupEnabledFlagValueIsSet() {
    parseCommand(
        "--privacy-enabled",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH,
        "--privacy-flexible-groups-enabled",
        "--min-gas-price",
        "0");

    final ArgumentCaptor<PrivacyParameters> privacyParametersArgumentCaptor =
        ArgumentCaptor.forClass(PrivacyParameters.class);

    verify(mockControllerBuilder).privacyParameters(privacyParametersArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    final PrivacyParameters privacyParameters = privacyParametersArgumentCaptor.getValue();
    assertThat(privacyParameters.isOnchainPrivacyGroupsEnabled()).isEqualTo(true);
  }

  @Test
  public void privacyMarkerTransactionSigningKeyFileRequiredIfMinGasPriceNonZero() {
    parseCommand("--privacy-enabled", "--privacy-public-key-file", ENCLAVE_PUBLIC_KEY_PATH);

    assertThat(commandErrorOutput.toString())
        .startsWith(
            "Not a free gas network. --privacy-marker-transaction-signing-key-file must be specified");
  }

  private Path createFakeGenesisFile(final JsonObject jsonGenesis) throws IOException {
    final Path genesisFile = Files.createTempFile("genesisFile", "");
    Files.write(genesisFile, encodeJsonGenesis(jsonGenesis).getBytes(UTF_8));
    genesisFile.toFile().deleteOnExit();
    return genesisFile;
  }

  private Path createTempFile(final String filename, final String contents) throws IOException {
    final Path file = Files.createTempFile(filename, "");
    Files.write(file, contents.getBytes(UTF_8));
    file.toFile().deleteOnExit();
    return file;
  }

  private Path createTempFile(final String filename, final byte[] contents) throws IOException {
    final Path file = Files.createTempFile(filename, "");
    Files.write(file, contents);
    file.toFile().deleteOnExit();
    return file;
  }

  private String encodeJsonGenesis(final JsonObject jsonGenesis) {
    return jsonGenesis.encodePrettily();
  }

  private static String escapeTomlString(final String s) {
    return StringEscapeUtils.escapeJava(s);
  }

  /**
   * Check logger calls
   *
   * <p>Here we check the calls to logger and not the result of the log line as we don't test the
   * logger itself but the fact that we call it.
   *
   * @param dependentOptions the string representing the list of dependent options names
   * @param mainOption the main option name
   */
  private void verifyOptionsConstraintLoggerCall(
      final String mainOption, final String... dependentOptions) {
    verify(mockLogger, atLeast(1))
        .warn(
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture(),
            stringArgumentCaptor.capture());
    assertThat(stringArgumentCaptor.getAllValues().get(0)).isEqualTo(DEPENDENCY_WARNING_MSG);

    for (final String option : dependentOptions) {
      assertThat(stringArgumentCaptor.getAllValues().get(1)).contains(option);
    }

    assertThat(stringArgumentCaptor.getAllValues().get(2)).isEqualTo(mainOption);
  }

  @Test
  public void privacyWithFastSyncMustError() {
    parseCommand("--sync-mode=FAST", "--privacy-enabled");

    assertThat(commandErrorOutput.toString()).contains("Fast sync cannot be enabled with privacy.");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Test
  public void privacyWithPruningMustError() {
    parseCommand("--pruning-enabled", "--privacy-enabled");

    assertThat(commandErrorOutput.toString()).contains("Pruning cannot be enabled with privacy.");
    assertThat(commandOutput.toString()).isEmpty();
  }

  @Rule public TemporaryFolder testFolder = new TemporaryFolder();

  @Test
  public void errorIsRaisedIfStaticNodesAreNotAllowed() throws IOException {
    final File staticNodesFile = testFolder.newFile("static-nodes.json");
    staticNodesFile.deleteOnExit();
    final File permissioningConfig = testFolder.newFile("permissioning");
    permissioningConfig.deleteOnExit();

    final EnodeURL staticNodeURI =
        EnodeURL.builder()
            .nodeId(
                "50203c6bfca6874370e71aecc8958529fd723feb05013dc1abca8fc1fff845c5259faba05852e9dfe5ce172a7d6e7c2a3a5eaa8b541c8af15ea5518bbff5f2fa")
            .ipAddress("127.0.0.1")
            .useDefaultPorts()
            .build();

    final EnodeURL allowedNode =
        EnodeURL.builder()
            .nodeId(
                "50203c6bfca6874370e71aecc8958529fd723feb05013dc1abca8fc1fff845c5259faba05852e9dfe5ce172a7d6e7c2a3a5eaa8b541c8af15ea5518bbff5f2fa")
            .useDefaultPorts()
            .ipAddress("127.0.0.1")
            .listeningPort(30304)
            .build();

    Files.write(
        staticNodesFile.toPath(), ("[\"" + staticNodeURI.toString() + "\"]").getBytes(UTF_8));
    Files.write(
        permissioningConfig.toPath(),
        ("nodes-allowlist=[\"" + allowedNode.toString() + "\"]").getBytes(UTF_8));

    parseCommand(
        "--data-path=" + testFolder.getRoot().getPath(),
        "--bootnodes",
        "--permissions-nodes-config-file-enabled=true",
        "--permissions-nodes-config-file=" + permissioningConfig.getPath());
    assertThat(commandErrorOutput.toString())
        .contains(staticNodeURI.toString(), "not in nodes-allowlist");
  }

  @Test
  public void pendingTransactionRetentionPeriod() {
    final int pendingTxRetentionHours = 999;
    parseCommand("--tx-pool-retention-hours", String.valueOf(pendingTxRetentionHours));
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());
    assertThat(transactionPoolConfigCaptor.getValue().getPendingTxRetentionPeriod())
        .isEqualTo(pendingTxRetentionHours);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void transactionPoolPriceBump() {
    final Percentage priceBump = Percentage.fromInt(13);
    parseCommand("--tx-pool-price-bump", priceBump.toString());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());
    assertThat(transactionPoolConfigCaptor.getValue().getPriceBump()).isEqualTo(priceBump);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void invalidTansactionPoolPriceBumpShouldFail() {
    parseCommand("--tx-pool-price-bump", "101");
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--tx-pool-price-bump'",
            "should be a number between 0 and 100 inclusive");
  }

  @Test
  public void transactionPoolTxFeeCap() {
    final Wei txFeeCap = Wei.fromEth(2);
    parseCommand("--rpc-tx-feecap", txFeeCap.toString());
    verify(mockControllerBuilder)
        .transactionPoolConfiguration(transactionPoolConfigCaptor.capture());
    assertThat(transactionPoolConfigCaptor.getValue().getTxFeeCap()).isEqualTo(txFeeCap);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void invalidTansactionPoolTxFeeCapShouldFail() {
    parseCommand("--rpc-tx-feecap", "abcd");
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option '--rpc-tx-feecap'", "cannot convert 'abcd' to Wei");
  }

  @Test
  public void txMessageKeepAliveSecondsWithInvalidInputShouldFail() {
    parseCommand("--Xincoming-tx-messages-keep-alive-seconds", "acbd");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--Xincoming-tx-messages-keep-alive-seconds': 'acbd' is not an int");
  }

  @Test
  public void eth65TrxAnnouncedBufferingPeriodWithInvalidInputShouldFail() {
    parseCommand("--Xeth65-tx-announced-buffering-period-milliseconds", "acbd");

    Mockito.verifyZeroInteractions(mockRunnerBuilder);

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Invalid value for option '--Xeth65-tx-announced-buffering-period-milliseconds': 'acbd' is not a long");
  }

  @Test
  public void tomlThatHasInvalidOptions() throws IOException {
    final URL configFile = this.getClass().getResource("/complete_config.toml");
    // update genesis file path, "similar" valid option and add invalid options
    final Path genesisFile = createFakeGenesisFile(GENESIS_VALID_JSON);
    final String updatedConfig =
        Resources.toString(configFile, UTF_8)
                .replace("/opt/besu/genesis.json", escapeTomlString(genesisFile.toString()))
                .replace("rpc-http-api", "rpc-http-apis")
            + System.lineSeparator()
            + "invalid_option=true"
            + System.lineSeparator()
            + "invalid_option2=true";

    final Path toml = createTempFile("toml", updatedConfig.getBytes(UTF_8));

    // Parse it.
    parseCommand("--config-file", toml.toString());

    assertThat(commandErrorOutput.toString())
        .contains("Unknown options in TOML configuration file: invalid_option, invalid_option2");
  }

  @Test
  public void targetGasLimitIsEnabledWhenSpecified() {
    parseCommand("--target-gas-limit=10000000");

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<GasLimitCalculator> gasLimitCalculatorArgumentCaptor =
        ArgumentCaptor.forClass(GasLimitCalculator.class);

    verify(mockControllerBuilder).gasLimitCalculator(gasLimitCalculatorArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(gasLimitCalculatorArgumentCaptor.getValue())
        .isEqualTo(new TargetingGasLimitCalculator(10_000_000L));
  }

  @Test
  public void targetGasLimitIsDisabledWhenNotSpecified() {
    parseCommand();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<GasLimitCalculator> gasLimitCalculatorArgumentCaptor =
        ArgumentCaptor.forClass(GasLimitCalculator.class);

    verify(mockControllerBuilder).gasLimitCalculator(gasLimitCalculatorArgumentCaptor.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(gasLimitCalculatorArgumentCaptor.getValue())
        .isEqualTo(GasLimitCalculator.constant());
  }

  @Test
  public void requiredBlocksSetWhenSpecified() {
    final long blockNumber = 8675309L;
    final String hash = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

    parseCommand("--required-block=" + blockNumber + "=" + hash);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(blockNumber);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(blockNumber, Hash.fromHexStringLenient(hash));
  }

  @Test
  public void requiredBlocksEmptyWhenNotSpecified() {
    parseCommand();

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(requiredBlocksArg.getValue()).isEmpty();
  }

  @Test
  public void requiredBlocksMulpleBlocksOneArg() {
    final long block1 = 8675309L;
    final long block2 = 5551212L;
    final String hash1 = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    final String hash2 = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    parseCommand("--required-block=" + block1 + "=" + hash1 + "," + block2 + "=" + hash2);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(block1, block2);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block1, Hash.fromHexStringLenient(hash1));
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block2, Hash.fromHexStringLenient(hash2));
  }

  @Test
  public void requiredBlocksMultipleBlocksTwoArgs() {
    final long block1 = 8675309L;
    final long block2 = 5551212L;
    final String hash1 = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
    final String hash2 = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    parseCommand(
        "--required-block=" + block1 + "=" + hash1, "--required-block=" + block2 + "=" + hash2);

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<Long, Hash>> requiredBlocksArg = ArgumentCaptor.forClass(Map.class);

    verify(mockControllerBuilder).requiredBlocks(requiredBlocksArg.capture());
    verify(mockControllerBuilder).build();

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();

    assertThat(requiredBlocksArg.getValue()).containsOnlyKeys(block1, block2);
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block1, Hash.fromHexStringLenient(hash1));
    assertThat(requiredBlocksArg.getValue())
        .containsEntry(block2, Hash.fromHexStringLenient(hash2));
  }

  @Test
  public void httpAuthenticationPublicKeyIsConfigured() throws IOException {
    final Path publicKey = Files.createTempFile("public_key", "");
    parseCommand("--rpc-http-authentication-jwt-public-key-file", publicKey.toString());

    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(jsonRpcConfigArgumentCaptor.getValue().getAuthenticationPublicKeyFile().getPath())
        .isEqualTo(publicKey.toString());
  }

  @Test
  public void httpAuthenticationWithoutRequiredConfiguredOptionsMustFail() {
    parseCommand("--rpc-http-enabled", "--rpc-http-authentication-enabled");

    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Unable to authenticate JSON-RPC HTTP endpoint without a supplied credentials file or authentication public key file");
  }

  @Test
  public void wsAuthenticationPublicKeyIsConfigured() throws IOException {
    final Path publicKey = Files.createTempFile("public_key", "");
    parseCommand("--rpc-ws-authentication-jwt-public-key-file", publicKey.toString());

    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();

    assertThat(wsRpcConfigArgumentCaptor.getValue().getAuthenticationPublicKeyFile().getPath())
        .isEqualTo(publicKey.toString());
  }

  @Test
  public void wsAuthenticationWithoutRequiredConfiguredOptionsMustFail() {
    parseCommand("--rpc-ws-enabled", "--rpc-ws-authentication-enabled");

    verifyNoInteractions(mockRunnerBuilder);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Unable to authenticate JSON-RPC WebSocket endpoint without a supplied credentials file or authentication public key file");
  }

  @Test
  public void privHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "PRIV", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "PRIV", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void eeaHttpApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-http-api", "EEA", "--rpc-http-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void eeaWsApisWithPrivacyDisabledLogsWarning() {
    parseCommand("--privacy-enabled=false", "--rpc-ws-api", "EEA", "--rpc-ws-enabled");

    verify(mockRunnerBuilder).build();
    verify(mockLogger)
        .warn("Privacy is disabled. Cannot use EEA/PRIV API methods when not using Privacy.");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void privEnclaveKeyFileDoesNotExist() {
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", "/non/existent/file");

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).startsWith("Problem with privacy-public-key-file");
    assertThat(commandErrorOutput.toString()).contains("No such file");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentTooShort() throws IOException {
    final Path file = createTempFile("privacy.key", "lkjashdfiluhwelrk");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString()).contains("needs to be 44 characters long");
  }

  @Test
  public void privEnclaveKeyFileInvalidContentNotValidBase64() throws IOException {
    final Path file = createTempFile("privacy.key", "l*jashdfillk9ashdfillkjashdfillkjashdfilrtg=");
    parseCommand("--privacy-enabled=true", "--privacy-public-key-file", file.toString());

    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .startsWith("Contents of privacy-public-key-file invalid");
    assertThat(commandErrorOutput.toString()).contains("Illegal base64 character");
  }

  @Test
  public void logLevelHasNullAsDefaultValue() {
    final TestBesuCommand command = parseCommand();

    assertThat(command.getLogLevel()).isNull();
  }

  @Test
  public void logLevelIsSetByLoggingOption() {
    final TestBesuCommand command = parseCommand("--logging", "WARN");

    assertThat(command.getLogLevel()).isEqualTo(Level.WARN);
  }

  @Test
  public void assertThatEnablingExperimentalEIPsWorks() {
    parseCommand("--Xeip1559-enabled=true");
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(ExperimentalEIPs.eip1559Enabled).isTrue();
  }

  @Test
  public void assertThatDisablingExperimentalEIPsWorks() {
    parseCommand("--Xeip1559-enabled=false");
    assertThat(commandErrorOutput.toString()).isEmpty();
    assertThat(ExperimentalEIPs.eip1559Enabled).isFalse();
  }

  @Test
  public void assertThatDefaultHttpTimeoutSecondsWorks() {
    parseCommand();
    assertThat(commandErrorOutput.toString()).isEmpty();
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHttpTimeoutSec())
        .isEqualTo(TimeoutOptions.defaultOptions().getTimeoutSeconds());
  }

  @Test
  public void assertThatHttpTimeoutSecondsWorks() {
    parseCommand("--Xhttp-timeout-seconds=513");
    assertThat(commandErrorOutput.toString()).isEmpty();
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getHttpTimeoutSec()).isEqualTo(513);
  }

  @Test
  public void assertThatInvalidHttpTimeoutSecondsFails() {
    parseCommand("--Xhttp-timeout-seconds=abc");
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option", "--Xhttp-timeout-seconds", "abc", "is not a long");
  }

  @Test
  public void assertThatDefaultWsTimeoutSecondsWorks() {
    parseCommand();
    assertThat(commandErrorOutput.toString()).isEmpty();
    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(wsRpcConfigArgumentCaptor.getValue().getTimeoutSec())
        .isEqualTo(TimeoutOptions.defaultOptions().getTimeoutSeconds());
  }

  @Test
  public void assertThatWsTimeoutSecondsWorks() {
    parseCommand("--Xws-timeout-seconds=11112018");
    assertThat(commandErrorOutput.toString()).isEmpty();
    verify(mockRunnerBuilder).webSocketConfiguration(wsRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(wsRpcConfigArgumentCaptor.getValue().getTimeoutSec()).isEqualTo(11112018);
  }

  @Test
  public void assertThatInvalidWsTimeoutSecondsFails() {
    parseCommand("--Xws-timeout-seconds=abc");
    assertThat(commandErrorOutput.toString())
        .contains("Invalid value for option", "--Xws-timeout-seconds", "abc", "is not a long");
  }

  @Test
  public void assertThatDuplicatePortSpecifiedFails() {
    parseCommand(
        "--p2p-port=9",
        "--rpc-http-enabled",
        "--rpc-http-port=10",
        "--rpc-ws-port=10",
        "--rpc-ws-enabled");
    assertThat(commandErrorOutput.toString())
        .contains("Port number '10' has been specified multiple times.");
  }

  @Test
  public void assertThatDuplicatePortZeroSucceeds() {
    parseCommand("--p2p-port=0", "--rpc-http-port=0", "--rpc-ws-port=0");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void assertThatNoPortsSpecifiedSucceeds() {
    parseCommand();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void assertThatAllPortsSpecifiedSucceeds() {
    parseCommand(
        "--p2p-port=9",
        "--graphql-http-port=10",
        "--rpc-http-port=11",
        "--rpc-ws-port=12",
        "--metrics-port=13",
        "--metrics-push-port=14",
        "--miner-stratum-port=15");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void compatibilityEth64ForkIdEnabledMustBeUsed() {
    parseCommand("--compatibility-eth64-forkid-enabled");
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isTrue();
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void compatibilityEth64ForkIdNotEnabledMustBeUsed() {
    parseCommand("--compatibility-eth64-forkid-enabled=false");
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isFalse();
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void assertThatCompatibilityEth64ForkIdIsNotEnabledByDefault() {
    parseCommand();
    verify(mockControllerBuilder)
        .ethProtocolConfiguration(ethProtocolConfigurationArgumentCaptor.capture());
    assertThat(ethProtocolConfigurationArgumentCaptor.getValue().isLegacyEth64ForkIdEnabled())
        .isFalse();
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void assertThatCompatibilityEth64ForkIdIsPresentInHelpMessage() {
    parseCommand("--help");
    assertThat(commandOutput.toString())
        .contains(
            "--compatibility-eth64-forkid-enabled",
            "Enable the legacy Eth/64 fork id. (default: false)");
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void quorumInteropDisabledDoesNotEnforceZeroGasPrice() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand(
        "--goquorum-compatibility-enabled=false", "--genesis-file", genesisFile.toString());
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void quorumInteropEnabledFailsWithoutGasPriceSet() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand("--goquorum-compatibility-enabled", "--genesis-file", genesisFile.toString());
    assertThat(commandErrorOutput.toString())
        .contains(
            "--min-gas-price must be set to zero if GoQuorum compatibility is enabled in the genesis config.");
  }

  @Test
  public void quorumInteropEnabledFailsWithoutGasPriceSetToZero() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "1");
    assertThat(commandErrorOutput.toString())
        .contains(
            "--min-gas-price must be set to zero if GoQuorum compatibility is enabled in the genesis config.");
  }

  @Test
  public void quorumInteropEnabledSucceedsWithGasPriceSetToZero() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "0",
        "--privacy-public-key-file",
        ENCLAVE_PUBLIC_KEY_PATH);
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void quorumInteropEnabledFailsIfEnclaveKeyFileDoesNotExist() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "0",
        "--privacy-public-key-file",
        "ThisFileDoesNotExist");
    assertThat(commandErrorOutput.toString())
        .contains(
            "--privacy-public-key-file must be set when --goquorum-compatibility-enabled is set to true.");
  }

  @Test
  public void quorumInteropEnabledFailsIfEnclaveKeyFileIsNotSet() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(VALID_GENESIS_QUORUM_INTEROP_ENABLED_WITH_CHAINID);
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "0");
    assertThat(commandErrorOutput.toString())
        .contains(
            "--privacy-public-key-file must be set when --goquorum-compatibility-enabled is set to true.");
  }

  @Test
  public void quorumInteropEnabledFailsWithMainnetDefaultNetwork() throws IOException {
    final Path genesisFile = createFakeGenesisFile(INVALID_GENESIS_QUORUM_INTEROP_ENABLED_MAINNET);
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "0");
    assertThat(commandErrorOutput.toString())
        .contains("GoQuorum compatibility mode (enabled) cannot be used on Mainnet");
  }

  @Test
  public void quorumInteropEnabledFailsWithMainnetChainId() throws IOException {
    final Path genesisFile =
        createFakeGenesisFile(INVALID_GENESIS_QUORUM_INTEROP_ENABLED_MAINNET.put("chainId", "1"));
    parseCommand(
        "--goquorum-compatibility-enabled",
        "--genesis-file",
        genesisFile.toString(),
        "--min-gas-price",
        "0");
    assertThat(commandErrorOutput.toString())
        .contains("GoQuorum compatibility mode (enabled) cannot be used on Mainnet");
  }

  @Test
  public void assertThatCheckPortClashAcceptsAsExpected() throws Exception {
    // use WS port for HTTP
    final int port = 8546;
    parseCommand("--rpc-http-enabled", "--rpc-http-port", String.valueOf(port));
    verify(mockRunnerBuilder).jsonRpcConfiguration(jsonRpcConfigArgumentCaptor.capture());
    verify(mockRunnerBuilder).build();
    assertThat(jsonRpcConfigArgumentCaptor.getValue().getPort()).isEqualTo(port);
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }

  @Test
  public void assertThatCheckPortClashRejectsAsExpected() throws Exception {
    // use WS port for HTTP
    final int port = 8546;
    parseCommand("--rpc-http-enabled", "--rpc-http-port", String.valueOf(port), "--rpc-ws-enabled");
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString())
        .contains(
            "Port number '8546' has been specified multiple times. Please review the supplied configuration.");
  }

  @Test
  public void staticNodesFileOptionValueAbsentMessage() {
    parseCommand("--static-nodes-file");
    assertThat(commandErrorOutput.toString()).startsWith("Missing required parameter for option");
  }

  @Test
  public void staticNodesFilesOptionInvalidJSONFormatError() throws IOException {
    final Path tempfile =
        createTempFile(
            "static-nodes-badformat.json",
            "\"enode://c0b0e1151971f8a22dc2493c622317c8706c731f6fcf46d93104ef"
                + "3a08f21f7750b5d5e17f311091f732c9f917b02e1ae6d39f076903779fd1e7"
                + "e7e6cd2fcef6@192.168.1.25:30303\"\n]");
    parseCommand("--static-nodes-file", tempfile.toString());
    assertThat(commandErrorOutput.toString()).startsWith("Failed to decode:Cannot deserialize ");
  }

  @Test
  public void staticNodesFileOptionFileDoesNotExistMessage() {
    parseCommand("--static-nodes-file", "this-file-does-not-exist-at-all.json");
    assertThat(commandErrorOutput.toString()).contains("Static nodes file", "does not exist");
  }

  @Test
  public void staticNodesFileOptionValidParamenter() throws IOException {
    final Path staticNodeTempFile =
        createTempFile(
            "static-nodes-goodformat.json",
            "[\n"
                + "\"enode://c0b0e1151971f8a22dc2493c622317c8706c731f6fcf46d93104ef"
                + "3a08f21f7750b5d5e17f311091f732c9f917b02e1ae6d39f076903779fd1e7"
                + "e7e6cd2fcef6@192.168.1.25:30303\"\n]");
    parseCommand("--static-nodes-file", staticNodeTempFile.toString());
    assertThat(commandOutput.toString()).isEmpty();
    assertThat(commandErrorOutput.toString()).isEmpty();
  }
}
