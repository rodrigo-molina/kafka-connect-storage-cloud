package io.confluent.connect.s3.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonServiceException.ErrorType;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult;
import com.amazonaws.services.s3.model.SSEAlgorithm;
import com.amazonaws.services.s3.model.UploadPartResult;
import io.confluent.connect.s3.S3SinkConnectorConfig;
import io.confluent.connect.s3.S3SinkConnectorTestBase;
import io.confluent.connect.s3.errors.FileExistsException;
import io.findify.s3mock.S3Mock;
import org.apache.kafka.connect.errors.ConnectException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.Map;

public class S3OutputStreamTest extends S3SinkConnectorTestBase {

  private S3OutputStream stream;
  final static String S3_TEST_KEY_NAME = "key";
  final static String S3_EXCEPTION_MESSAGE = "this is an s3 exception";
  private AmazonS3 s3Mock;

  @Captor
  ArgumentCaptor<InitiateMultipartUploadRequest> initMultipartRequestCaptor;

  @Captor
  ArgumentCaptor<CompleteMultipartUploadRequest> completeMultipartRequestCaptor;

  @Before
  public void before() throws Exception {
    super.setUp();
    s3Mock = mock(AmazonS3.class);
    stream = new S3OutputStream(S3_TEST_KEY_NAME, connectorConfig, s3Mock);
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testPropagateUnretriableS3Exceptions() {
    AmazonServiceException e = new AmazonServiceException(S3_EXCEPTION_MESSAGE);
    e.setErrorType(ErrorType.Client);

    when(s3Mock.initiateMultipartUpload(any())).thenThrow(e);
    assertThrows(IOException.class, () -> stream.commit());
  }

  @Test
  public void testPropagateRetriableS3Exceptions() {
    AmazonServiceException e = new AmazonServiceException(S3_EXCEPTION_MESSAGE);
    e.setErrorType(ErrorType.Service);

    when(s3Mock.initiateMultipartUpload(any())).thenThrow(e);
    assertThrows(IOException.class, () -> stream.commit());
  }

  @Test
  public void testPropagateOtherRetriableS3Exceptions() {
    when(s3Mock.initiateMultipartUpload(any())).thenThrow(new AmazonClientException(S3_EXCEPTION_MESSAGE));
    assertThrows(IOException.class, () -> stream.commit());
  }

  @Test
  public void testNewMultipartUploadAESSSE() throws IOException {

    Map<String, String> props = createProps();
    props.put(S3SinkConnectorConfig.SSEA_CONFIG, SSEAlgorithm.AES256.toString());
    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    stream.newMultipartUpload();

    verify(s3Mock).initiateMultipartUpload(initMultipartRequestCaptor.capture());

    assertNotNull(initMultipartRequestCaptor.getValue());
    assertNotNull(initMultipartRequestCaptor.getValue().getObjectMetadata());
    assertNull(initMultipartRequestCaptor.getValue().getSSECustomerKey());
    assertNull(initMultipartRequestCaptor.getValue().getSSEAwsKeyManagementParams());
  }

  @Test
  public void testNewMultipartUploadKMSSSE() throws IOException {

    Map<String, String> props = createProps();
    props.put(S3SinkConnectorConfig.SSEA_CONFIG, SSEAlgorithm.KMS.toString());
    props.put(S3SinkConnectorConfig.SSE_KMS_KEY_ID_CONFIG, "key1");
    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    stream.newMultipartUpload();

    verify(s3Mock).initiateMultipartUpload(initMultipartRequestCaptor.capture());

    assertNotNull(initMultipartRequestCaptor.getValue());
    assertNull(initMultipartRequestCaptor.getValue().getObjectMetadata());
    assertNotNull(initMultipartRequestCaptor.getValue().getSSEAwsKeyManagementParams());
    assertNull(initMultipartRequestCaptor.getValue().getSSECustomerKey());

  }

  @Test
  public void testNewMultipartUploadCustomerKeySSE() throws IOException {

    Map<String, String> props = createProps();
    props.put(S3SinkConnectorConfig.SSEA_CONFIG, SSEAlgorithm.AES256.toString());
    props.put(S3SinkConnectorConfig.SSE_CUSTOMER_KEY, "key1");
    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    stream.newMultipartUpload();

    verify(s3Mock).initiateMultipartUpload(initMultipartRequestCaptor.capture());

    assertNotNull(initMultipartRequestCaptor.getValue());
    assertNull(initMultipartRequestCaptor.getValue().getObjectMetadata());
    assertNotNull(initMultipartRequestCaptor.getValue().getSSECustomerKey());
    assertNull(initMultipartRequestCaptor.getValue().getSSEAwsKeyManagementParams());

  }

  @Test
  public void testNewMultipartUploadDefaultSSE() throws IOException {
    stream = new S3OutputStream(S3_TEST_KEY_NAME, connectorConfig, s3Mock);

    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    stream.newMultipartUpload();

    verify(s3Mock).initiateMultipartUpload(initMultipartRequestCaptor.capture());

    assertNotNull(initMultipartRequestCaptor.getValue());
    assertNull(initMultipartRequestCaptor.getValue().getObjectMetadata());
    assertNull(initMultipartRequestCaptor.getValue().getSSECustomerKey());
    assertNull(initMultipartRequestCaptor.getValue().getSSEAwsKeyManagementParams());
  }

  @Test
  public void testCompleteMultipartUploadWithConditionalWrites() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "true");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    when(s3Mock.completeMultipartUpload(any())).thenReturn(mock(CompleteMultipartUploadResult.class));
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertEquals("*", completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }

  @Test
  public void testCompleteMultipartUploadWithoutConditionalWrites() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "false");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    when(s3Mock.completeMultipartUpload(any())).thenReturn(mock(CompleteMultipartUploadResult.class));
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertNull(completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }

  @Test(expected = FileExistsException.class)
  public void testCompleteMultipartUploadThrowsExceptionWhenFileExists() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "true");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    AmazonS3Exception exception = new AmazonS3Exception("file exists");
    exception.setStatusCode(412);

    when(s3Mock.completeMultipartUpload(any())).thenThrow(exception);
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));
    when(s3Mock.doesObjectExist(any(), any())).thenReturn(true);

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertNull(completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }

  @Test(expected = FileExistsException.class)
  public void testCompleteMultipartUploadThrowsExceptionWhenS3Returns200WithPreconditionFailedError() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "true");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    AmazonS3Exception exception = new AmazonS3Exception("file exists");
    exception.setStatusCode(200);
    exception.setErrorCode("PreconditionFailed");

    when(s3Mock.completeMultipartUpload(any())).thenThrow(exception);
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));
    when(s3Mock.doesObjectExist(any(), any())).thenReturn(true);

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertNull(completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }

  @Test(expected = IOException.class)
  public void testCompleteMultipartUploadRethrowsExceptionWhenAmazonS3Exception() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "true");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    AmazonS3Exception exception = new AmazonS3Exception("file conflict");
    exception.setStatusCode(422);

    when(s3Mock.completeMultipartUpload(any())).thenThrow(exception);
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertNull(completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }

  @Test(expected = ConnectException.class)
  public void testCompleteMultipartUploadWithIncorrectS3ResponseThrowsConnectException() throws IOException {
    Map<String, String> props = super.createProps();
    props.put(S3SinkConnectorConfig.ENABLE_CONDITIONAL_WRITES_CONFIG, "true");
    props.put(S3SinkConnectorConfig.ROTATE_SCHEDULE_INTERVAL_MS_CONFIG, "100");

    stream = new S3OutputStream(S3_TEST_KEY_NAME, new S3SinkConnectorConfig(props), s3Mock);

    AmazonS3Exception exception = new AmazonS3Exception("file exists");
    exception.setStatusCode(412);

    when(s3Mock.completeMultipartUpload(any())).thenThrow(exception);
    when(s3Mock.initiateMultipartUpload(any())).thenReturn(mock(InitiateMultipartUploadResult.class));
    when(s3Mock.uploadPart(any())).thenReturn(mock(UploadPartResult.class));
    when(s3Mock.doesObjectExist(any(), any())).thenReturn(false);

    stream.commit();

    verify(s3Mock).completeMultipartUpload(completeMultipartRequestCaptor.capture());

    assertNotNull(completeMultipartRequestCaptor.getValue());
    assertNull(completeMultipartRequestCaptor.getValue().getIfNoneMatch());
  }
}
