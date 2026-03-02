import type {
  ComposeAlignment,
  ComposeArrangement,
  ComposeBorder,
  ComposeCommonProps,
  ComposeNodeFactory,
  ComposeShape,
  ComposeTextFieldStyle,
  ComposeTextStyle
} from "./compose-dsl";

/**
 * AUTO-GENERATED from Compose Material3/Foundation source signatures.
 * Do not edit manually. Regenerate via tools/compose_dsl/generate_compose_dsl_artifacts.py.
 */

export interface ComposeGeneratedColumnProps extends ComposeCommonProps {
  horizontalAlignment?: ComposeAlignment;
  verticalArrangement?: ComposeArrangement;
}

export interface ComposeGeneratedRowProps extends ComposeCommonProps {
  horizontalArrangement?: ComposeArrangement;
  onClick?: () => void | Promise<void>;
  verticalAlignment?: ComposeAlignment;
}

export interface ComposeGeneratedBoxProps extends ComposeCommonProps {
  propagateMinConstraints?: boolean;
}

export interface ComposeGeneratedSpacerProps extends ComposeCommonProps {
}

export interface ComposeGeneratedLazyColumnProps extends ComposeCommonProps {
  horizontalAlignment?: ComposeAlignment;
  reverseLayout?: boolean;
  spacing?: number;
  verticalArrangement?: ComposeArrangement;
}

export interface ComposeGeneratedLazyRowProps extends ComposeCommonProps {
  horizontalArrangement?: ComposeArrangement;
  reverseLayout?: boolean;
  verticalAlignment?: ComposeAlignment;
}

export interface ComposeGeneratedTextProps extends ComposeCommonProps {
  color?: string;
  fontWeight?: string;
  maxLines?: number;
  softWrap?: boolean;
  style?: ComposeTextStyle;
  text: string;
}

export interface ComposeGeneratedTextFieldProps extends ComposeCommonProps {
  enabled?: boolean;
  isError?: boolean;
  isPassword?: boolean;
  label?: string;
  maxLines?: number;
  minLines?: number;
  onValueChange: (value: string) => void;
  placeholder?: string;
  readOnly?: boolean;
  singleLine?: boolean;
  style?: ComposeTextFieldStyle;
  value: string;
}

export interface ComposeGeneratedSwitchProps extends ComposeCommonProps {
  checked: boolean;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
}

export interface ComposeGeneratedCheckboxProps extends ComposeCommonProps {
  checked: boolean;
  enabled?: boolean;
  onCheckedChange: (checked: boolean) => void;
}

export interface ComposeGeneratedButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedIconButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedCardProps extends ComposeCommonProps {
  border?: ComposeBorder;
  containerColor?: string;
  contentColor?: string;
  elevation?: number;
  shape?: ComposeShape;
}

export interface ComposeGeneratedSurfaceProps extends ComposeCommonProps {
  alpha?: number;
  color?: string;
  containerColor?: string;
  contentColor?: string;
  shadowElevation?: number;
  shape?: ComposeShape;
  tonalElevation?: number;
}

export interface ComposeGeneratedIconProps extends ComposeCommonProps {
  contentDescription?: string;
  name?: string;
  size?: number;
  tint?: string;
}

export interface ComposeGeneratedLinearProgressIndicatorProps extends ComposeCommonProps {
  color?: string;
  progress?: number;
}

export interface ComposeGeneratedCircularProgressIndicatorProps extends ComposeCommonProps {
  color?: string;
  strokeWidth?: number;
}

export interface ComposeGeneratedSnackbarHostProps extends ComposeCommonProps {
}

export interface ComposeGeneratedBadgeProps extends ComposeCommonProps {
  contentColor?: string;
}

export interface ComposeGeneratedDismissibleDrawerSheetProps extends ComposeCommonProps {
  drawerTonalElevation?: number;
}

export interface ComposeGeneratedDividerProps extends ComposeCommonProps {
  color?: string;
  thickness?: number;
}

export interface ComposeGeneratedElevatedButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedElevatedCardProps extends ComposeCommonProps {
}

export interface ComposeGeneratedExtendedFloatingActionButtonProps extends ComposeCommonProps {
  contentColor?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedFilledIconButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedFilledTonalButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedFilledTonalIconButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedFloatingActionButtonProps extends ComposeCommonProps {
  contentColor?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedHorizontalDividerProps extends ComposeCommonProps {
  color?: string;
  thickness?: number;
}

export interface ComposeGeneratedIconToggleButtonProps extends ComposeCommonProps {
  checked: boolean;
  enabled?: boolean;
  icon?: string;
  onCheckedChange: (checked: boolean) => void;
}

export interface ComposeGeneratedLargeFloatingActionButtonProps extends ComposeCommonProps {
  contentColor?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedMaterialThemeProps extends ComposeCommonProps {
}

export interface ComposeGeneratedModalDrawerSheetProps extends ComposeCommonProps {
  drawerTonalElevation?: number;
}

export interface ComposeGeneratedNavigationBarProps extends ComposeCommonProps {
  contentColor?: string;
  tonalElevation?: number;
}

export interface ComposeGeneratedNavigationRailProps extends ComposeCommonProps {
  contentColor?: string;
}

export interface ComposeGeneratedOutlinedButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedOutlinedCardProps extends ComposeCommonProps {
}

export interface ComposeGeneratedOutlinedIconButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  icon?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedPermanentDrawerSheetProps extends ComposeCommonProps {
  drawerTonalElevation?: number;
}

export interface ComposeGeneratedProvideTextStyleProps extends ComposeCommonProps {
  style?: ComposeTextStyle;
}

export interface ComposeGeneratedScaffoldProps extends ComposeCommonProps {
  contentColor?: string;
}

export interface ComposeGeneratedSmallFloatingActionButtonProps extends ComposeCommonProps {
  contentColor?: string;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedSnackbarProps extends ComposeCommonProps {
  actionOnNewLine?: boolean;
  contentColor?: string;
}

export interface ComposeGeneratedTabProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
  selected: boolean;
}

export interface ComposeGeneratedTextButtonProps extends ComposeCommonProps {
  enabled?: boolean;
  onClick: () => void | Promise<void>;
}

export interface ComposeGeneratedVerticalDividerProps extends ComposeCommonProps {
  color?: string;
  thickness?: number;
}

export interface ComposeGeneratedBoxWithConstraintsProps extends ComposeCommonProps {
  propagateMinConstraints?: boolean;
}

export interface ComposeGeneratedBasicTextProps extends ComposeCommonProps {
  maxLines?: number;
  softWrap?: boolean;
  style?: ComposeTextStyle;
  text: string;
}

export interface ComposeGeneratedDisableSelectionProps extends ComposeCommonProps {
}

export interface ComposeGeneratedImageProps extends ComposeCommonProps {
  alpha?: number;
  contentDescription: string;
  name?: string;
}

export interface ComposeGeneratedSelectionContainerProps extends ComposeCommonProps {
}

export interface ComposeMaterial3GeneratedUiFactoryRegistry {
  Column: ComposeNodeFactory<ComposeGeneratedColumnProps>;
  Row: ComposeNodeFactory<ComposeGeneratedRowProps>;
  Box: ComposeNodeFactory<ComposeGeneratedBoxProps>;
  Spacer: ComposeNodeFactory<ComposeGeneratedSpacerProps>;
  LazyColumn: ComposeNodeFactory<ComposeGeneratedLazyColumnProps>;
  LazyRow: ComposeNodeFactory<ComposeGeneratedLazyRowProps>;
  Text: ComposeNodeFactory<ComposeGeneratedTextProps>;
  TextField: ComposeNodeFactory<ComposeGeneratedTextFieldProps>;
  Switch: ComposeNodeFactory<ComposeGeneratedSwitchProps>;
  Checkbox: ComposeNodeFactory<ComposeGeneratedCheckboxProps>;
  Button: ComposeNodeFactory<ComposeGeneratedButtonProps>;
  IconButton: ComposeNodeFactory<ComposeGeneratedIconButtonProps>;
  Card: ComposeNodeFactory<ComposeGeneratedCardProps>;
  Surface: ComposeNodeFactory<ComposeGeneratedSurfaceProps>;
  Icon: ComposeNodeFactory<ComposeGeneratedIconProps>;
  LinearProgressIndicator: ComposeNodeFactory<ComposeGeneratedLinearProgressIndicatorProps>;
  CircularProgressIndicator: ComposeNodeFactory<ComposeGeneratedCircularProgressIndicatorProps>;
  SnackbarHost: ComposeNodeFactory<ComposeGeneratedSnackbarHostProps>;
  Badge: ComposeNodeFactory<ComposeGeneratedBadgeProps>;
  DismissibleDrawerSheet: ComposeNodeFactory<ComposeGeneratedDismissibleDrawerSheetProps>;
  Divider: ComposeNodeFactory<ComposeGeneratedDividerProps>;
  ElevatedButton: ComposeNodeFactory<ComposeGeneratedElevatedButtonProps>;
  ElevatedCard: ComposeNodeFactory<ComposeGeneratedElevatedCardProps>;
  ExtendedFloatingActionButton: ComposeNodeFactory<ComposeGeneratedExtendedFloatingActionButtonProps>;
  FilledIconButton: ComposeNodeFactory<ComposeGeneratedFilledIconButtonProps>;
  FilledTonalButton: ComposeNodeFactory<ComposeGeneratedFilledTonalButtonProps>;
  FilledTonalIconButton: ComposeNodeFactory<ComposeGeneratedFilledTonalIconButtonProps>;
  FloatingActionButton: ComposeNodeFactory<ComposeGeneratedFloatingActionButtonProps>;
  HorizontalDivider: ComposeNodeFactory<ComposeGeneratedHorizontalDividerProps>;
  IconToggleButton: ComposeNodeFactory<ComposeGeneratedIconToggleButtonProps>;
  LargeFloatingActionButton: ComposeNodeFactory<ComposeGeneratedLargeFloatingActionButtonProps>;
  MaterialTheme: ComposeNodeFactory<ComposeGeneratedMaterialThemeProps>;
  ModalDrawerSheet: ComposeNodeFactory<ComposeGeneratedModalDrawerSheetProps>;
  NavigationBar: ComposeNodeFactory<ComposeGeneratedNavigationBarProps>;
  NavigationRail: ComposeNodeFactory<ComposeGeneratedNavigationRailProps>;
  OutlinedButton: ComposeNodeFactory<ComposeGeneratedOutlinedButtonProps>;
  OutlinedCard: ComposeNodeFactory<ComposeGeneratedOutlinedCardProps>;
  OutlinedIconButton: ComposeNodeFactory<ComposeGeneratedOutlinedIconButtonProps>;
  PermanentDrawerSheet: ComposeNodeFactory<ComposeGeneratedPermanentDrawerSheetProps>;
  ProvideTextStyle: ComposeNodeFactory<ComposeGeneratedProvideTextStyleProps>;
  Scaffold: ComposeNodeFactory<ComposeGeneratedScaffoldProps>;
  SmallFloatingActionButton: ComposeNodeFactory<ComposeGeneratedSmallFloatingActionButtonProps>;
  Snackbar: ComposeNodeFactory<ComposeGeneratedSnackbarProps>;
  Tab: ComposeNodeFactory<ComposeGeneratedTabProps>;
  TextButton: ComposeNodeFactory<ComposeGeneratedTextButtonProps>;
  VerticalDivider: ComposeNodeFactory<ComposeGeneratedVerticalDividerProps>;
  BoxWithConstraints: ComposeNodeFactory<ComposeGeneratedBoxWithConstraintsProps>;
  BasicText: ComposeNodeFactory<ComposeGeneratedBasicTextProps>;
  DisableSelection: ComposeNodeFactory<ComposeGeneratedDisableSelectionProps>;
  Image: ComposeNodeFactory<ComposeGeneratedImageProps>;
  SelectionContainer: ComposeNodeFactory<ComposeGeneratedSelectionContainerProps>;
}

