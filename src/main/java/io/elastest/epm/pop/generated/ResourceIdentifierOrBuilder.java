package io.elastest.epm.pop.generated;// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: client.proto

public interface ResourceIdentifierOrBuilder
    extends
    // @@protoc_insertion_point(interface_extends:ResourceIdentifier)
    com.google.protobuf.MessageOrBuilder {

  /** <code>optional string resource_id = 1;</code> */
  String getResourceId();
  /** <code>optional string resource_id = 1;</code> */
  com.google.protobuf.ByteString getResourceIdBytes();

  /** <code>optional .Auth auth = 2;</code> */
  boolean hasAuth();
  /** <code>optional .Auth auth = 2;</code> */
  Auth getAuth();
  /** <code>optional .Auth auth = 2;</code> */
  AuthOrBuilder getAuthOrBuilder();
}
