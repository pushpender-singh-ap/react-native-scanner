import React from 'react';
import { Text, View } from 'react-native';
import type { ScannerViewProps } from "./ScannerViewTypes";

const ScannerViewComponent = React.FunctionComponent<ScannerViewProps> = () => (
    <View style={{ alignSelf: 'flex-start' }}>
        <Text style={{ color: 'red' }}>
            React Native Scanner View does not support this platform.
        </Text>
    </View>
);

export default ScannerViewComponent;