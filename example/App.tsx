import React, {useEffect, useState} from 'react';
import {
  Alert,
  Platform,
  useWindowDimensions,
  Text,
  SafeAreaView,
} from 'react-native';

import {
  request,
  PERMISSIONS,
  openSettings,
  RESULTS,
} from 'react-native-permissions';
import {ReactNativeScannerView} from '@pushpendersingh/react-native-scanner';

export default function App() {
  const {height, width} = useWindowDimensions();
  const [isCameraPermissionGranted, setIsCameraPermissionGranted] =
    useState(false);

  useEffect(() => {
    checkCameraPermission();
  }, []);

  const checkCameraPermission = async () => {
    request(
      Platform.OS === 'ios'
        ? PERMISSIONS.IOS.CAMERA
        : PERMISSIONS.ANDROID.CAMERA,
    ).then(async (result: any) => {
      switch (result) {
        case RESULTS.UNAVAILABLE:
          // console.log('This feature is not available (on this device / in this context)');
          break;
        case RESULTS.DENIED:
          Alert.alert(
            'Permission Denied',
            'You need to grant camera permission first',
          );
          openSettings();
          break;
        case RESULTS.GRANTED:
          setIsCameraPermissionGranted(true);
          break;
        case RESULTS.BLOCKED:
          Alert.alert(
            'Permission Blocked',
            'You need to grant camera permission first',
          );
          openSettings();
          break;
      }
    });
  };

  if (isCameraPermissionGranted) {
    return (
      <SafeAreaView style={{flex: 1}}>
        <ReactNativeScannerView
          style={{height, width}}
          onQrScanned={(value: any) => {
            console.log(value.nativeEvent);
          }}
        />
      </SafeAreaView>
    );
  } else {
    return (
      <Text style={{fontSize: 30, color: 'red'}}>
        You need to grant camera permission first
      </Text>
    );
  }
}