import SwiftUI

struct CurrencyRowView: View {
    @ObservedObject var vm: ConverterViewModel
    let code: String
    var focusedCode: FocusState<String?>.Binding

    private var isActive: Bool { vm.activeCode == code }

    var body: some View {
        VStack(spacing: 2) {
            HStack(spacing: 8) {
                Text(CurrencyData.flag(code)).font(.title2)
                Text(code).font(.subheadline.bold())
                Spacer()
                Text(CurrencyData.symbol(code))
                    .font(.footnote).foregroundColor(.gray)
                amountField
            }
            HStack {
                Text(CurrencyData.name(code))
                    .font(.caption2).foregroundColor(.secondary)
                Spacer()
                Text(vm.rateLine(code))
                    .font(.caption2).foregroundColor(Color(hex: "AAAAAA"))
            }
        }
        .padding(.horizontal, 12).padding(.vertical, 10)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(RoundedRectangle(cornerRadius: 10)
            .stroke(isActive ? Color.brandBlue : .clear, lineWidth: 2))
        .contentShape(Rectangle())
    }

    @ViewBuilder private var amountField: some View {
        if isActive {
            TextField("0", text: Binding(
                get: { vm.inputText },
                set: { vm.inputChanged($0) }))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.title3.bold())
                .frame(width: 130)
                .focused(focusedCode, equals: code)
        } else {
            Text(SmartFormat.amount(vm.converted(code), decimals: vm.decimals))
                .font(.title3.bold())
                .frame(width: 130, alignment: .trailing)
                .onTapGesture {
                    vm.setActive(code)
                    focusedCode.wrappedValue = code
                }
        }
    }
}
