package io.elastest.epm.pop.adapter.compose;

import com.google.protobuf.ByteString;
import io.elastest.epm.core.NetworkManagement;
import io.elastest.epm.model.*;
import io.elastest.epm.pop.adapter.compose.generated.ComposeHandlerGrpc;
import io.elastest.epm.pop.adapter.compose.generated.ComposeIdentifier;
import io.elastest.epm.pop.adapter.compose.generated.ComposePackage;
import io.elastest.epm.pop.adapter.compose.generated.ResourceGroupCompose;
import io.elastest.epm.pop.adapter.exception.AdapterException;
import io.elastest.epm.properties.DockerProperties;
import io.elastest.epm.repository.NetworkRepository;
import io.elastest.epm.repository.PoPRepository;
import io.elastest.epm.repository.ResourceGroupRepository;
import io.elastest.epm.repository.VduRepository;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.PostConstruct;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DockerComposeAdapter {

  private Logger log = LoggerFactory.getLogger(this.getClass());

  @Autowired private PoPRepository poPRepository;

  @Autowired private NetworkRepository networkRepository;

  @Autowired private NetworkManagement networkManagement;

  @Autowired private VduRepository vduRepository;

  @Autowired private ResourceGroupRepository resourceGroupRepository;

  @Autowired private DockerProperties dockerProperties;

  private ManagedChannel channel;
  private ComposeHandlerGrpc.ComposeHandlerBlockingStub blockingStub;

  @PostConstruct
  public void init() {
    // TODO: FIX GET IP AND PORT FROM SETTINGS
    ManagedChannelBuilder<?> channelBuilder =
        ManagedChannelBuilder.forAddress(dockerProperties.getCompose_ip(), 50051)
            .usePlaintext(true);
    channel = channelBuilder.build();
    blockingStub = ComposeHandlerGrpc.newBlockingStub(channel);
  }

  public ResourceGroup upCompose(InputStream inputStream) throws IOException {

    ByteString yamlFile = ByteString.copyFrom(IOUtils.toByteArray(inputStream));
    ComposePackage composePackage = ComposePackage.newBuilder().setComposeFile(yamlFile).build();
    ResourceGroupCompose rg = blockingStub.upCompose(composePackage);

    ResourceGroup resourceGroup = new ResourceGroup();
    resourceGroup.setName(rg.getName());

    for (ResourceGroupCompose.PoPCompose poPCompose : rg.getPopsList()) {
      if (poPRepository.findOneByName(poPCompose.getName()) == null) {
        PoP poP = new PoP();
        poP.setName(poPCompose.getName());
        poP.setInterfaceEndpoint(poPCompose.getInterfaceEndpoint());
        poPRepository.save(poP);
        resourceGroup.addPopsItem(poP);
      }
    }

    for (ResourceGroupCompose.NetworkCompose networkCompose : rg.getNetworksList()) {
      Network network = new Network();
      network.setName(networkCompose.getName());
      network.setCidr(networkCompose.getCidr());
      network.setPoPName(networkCompose.getPoPName());
      network.setNetworkId(networkCompose.getNetworkId());
      networkRepository.save(network);
      resourceGroup.addNetworksItem(network);
    }

    for (ResourceGroupCompose.VDUCompose vduCompose : rg.getVdusList()) {

      VDU vdu = new VDU();
      vdu.setName(vduCompose.getName());
      vdu.setImageName(vduCompose.getImageName());
      vdu.setComputeId(vduCompose.getComputeId());
      vdu.setNetName(vduCompose.getNetName());
      vdu.setPoPName(vduCompose.getPoPName());
      vdu.setIp(vduCompose.getIp());
      for (ResourceGroupCompose.MetadataEntryCompose metadataEntryCompose :
          vduCompose.getMetadataList()) {
        KeyValuePair kvp =
            new KeyValuePair(metadataEntryCompose.getKey(), metadataEntryCompose.getValue());
        vdu.addMetadataItem(kvp);
      }
      vduRepository.save(vdu);
      resourceGroup.addVdusItem(vdu);
    }

    resourceGroupRepository.save(resourceGroup);
    return resourceGroup;
  }

  public void rmCompose(String composeId) {

    ComposeIdentifier composeIdentifier =
        ComposeIdentifier.newBuilder().setComposeId(composeId).build();
    blockingStub.removeCompose(composeIdentifier);

    ResourceGroup resourceGroup = resourceGroupRepository.findOneByName(composeId);

    vduRepository.delete(resourceGroup.getVdus());

    for (Network network : resourceGroup.getNetworks()) {
      try {
        networkManagement.deleteNetwork(network.getId());
      } catch (AdapterException exception) {
        log.warn("Could not delete network: " + network.getId());
        log.warn(exception.getMessage());
      }
    }
    poPRepository.delete(resourceGroup.getPops());
    resourceGroupRepository.delete(resourceGroup);
  }
}
